package com.miladgeo.walkie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var talk: Button
    private lateinit var hostButton: Button
    private lateinit var joinButton: Button
    private lateinit var disconnectButton: Button
    private var radio: LocalRadio? = null
    private val talking = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 16000
    private val frameBytes = 640 // 20 ms, mono PCM16 at 16 kHz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 35, 47)
        window.navigationBarColor = Color.rgb(9, 35, 47)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(9, 35, 47))
        }
        val title = TextView(this).apply {
            text = "بی‌سیم کارگاهی\nMILADGEO"
            textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 105))
        status = TextView(this).apply {
            text = "برای شروع، گوشی میزبان هات‌اسپات را روشن کند."
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.rgb(179, 220, 225))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 90))
        hostButton = makeButton("شروع بی‌سیم — میزبان", Color.rgb(18, 126, 110))
        root.addView(hostButton, buttonParams())
        joinButton = makeButton("پیوستن خودکار به میزبان", Color.rgb(39, 91, 148))
        root.addView(joinButton, buttonParams())
        talk = makeButton("برای صحبت نگه دار", Color.rgb(217, 111, 27)).apply { isEnabled = false }
        root.addView(talk, LinearLayout.LayoutParams(-1, 180).apply { topMargin = 20; bottomMargin = 16 })
        disconnectButton = makeButton("قطع اتصال", Color.rgb(91, 103, 111))
        root.addView(disconnectButton, buttonParams())
        val note = TextView(this).apply {
            text = "ابتدا هات‌اسپات گوشی میزبان را از تنظیمات اندروید روشن کن. گوشی دوم به همان شبکه وصل شود. برای مکالمه، دکمه نارنجی را نگه دار. نسخه آزمایشی؛ ممکن است بعضی هات‌اسپات‌ها ارتباط مستقیم گوشی‌ها را مسدود کنند."
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY); setPadding(6, 18, 6, 0)
        }
        root.addView(note, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        hostButton.setOnClickListener {
            if (!hasMicPermission()) return@setOnClickListener
            disconnect()
            val r = LocalRadio(::updateStatus)
            radio = r
            r.startHost()
        }
        joinButton.setOnClickListener {
            if (!hasMicPermission()) return@setOnClickListener
            disconnect()
            val r = LocalRadio(::updateStatus)
            radio = r
            r.findAndJoin()
        }
        disconnectButton.setOnClickListener { stopTalking(); disconnect(); updateStatus("اتصال قطع شد.") }
        talk.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { if (radio?.isConnected() == true) startTalking(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopTalking(); true }
                else -> true
            }
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(-1, 58).apply { topMargin = 7; bottomMargin = 7 }
    private fun makeButton(label: String, color: Int) = Button(this).apply {
        text = label; textSize = 16f; setTextColor(Color.WHITE); setBackgroundColor(color); isAllCaps = false
    }
    private fun hasMicPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        updateStatus("برای مکالمه، اجازه میکروفن را بده و دوباره تلاش کن.")
        return false
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) updateStatus("مجوز میکروفن داده شد. گوشی میزبان می‌تواند شروع کند.")
    }

    private fun updateStatus(message: String) = runOnUiThread {
        status.text = message
        talk.isEnabled = radio?.isConnected() == true
    }

    @SuppressLint("MissingPermission")
    private fun startTalking() {
        if (!talking.compareAndSet(false, true)) return
        talk.text = "در حال ارسال صدا… رها کن برای قطع"
        try {
            val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frameBytes * 4))
            audioRecord?.startRecording()
            captureThread = thread(name = "PTT-Capture") {
                val buffer = ByteArray(frameBytes)
                while (talking.get()) {
                    val n = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (n > 0 && talking.get()) radio?.sendAudio(if (n == buffer.size) buffer.copyOf() else buffer.copyOf(n))
                }
            }
        } catch (e: Exception) {
            talking.set(false); updateStatus("خطای میکروفن: ${e.message ?: "نامشخص"}"); stopTalking()
        }
    }

    private fun stopTalking() {
        talking.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        talk.text = "برای صحبت نگه دار"
    }

    private fun playAudio(bytes: ByteArray) {
        try {
            if (audioTrack == null) {
                val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min, frameBytes * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                audioTrack?.play()
            }
            audioTrack?.write(bytes, 0, bytes.size)
        } catch (_: Exception) {}
    }

    private fun disconnect() {
        stopTalking()
        radio?.close(); radio = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        talk.isEnabled = false
    }
    override fun onDestroy() { disconnect(); super.onDestroy() }

    private inner class LocalRadio(private val statusCallback: (String) -> Unit) {
        private val running = AtomicBoolean(true)
        private val clients = CopyOnWriteArrayList<Client>()
        @Volatile private var server: ServerSocket? = null
        @Volatile private var clientSocket: Socket? = null
        @Volatile private var connected = false
        @Volatile private var hostMode = false
        fun isConnected() = connected

        fun startHost() {
            hostMode = true
            thread(name = "Radio-TCP-Server") {
                try {
                    server = ServerSocket(47655)
                    connected = true
                    statusCallback("میزبان آماده است. حالا گوشی دوم «پیوستن خودکار» را بزند.")
                    while (running.get()) {
                        val socket = server!!.accept()
                        socket.tcpNoDelay = true
                        val c = Client(socket)
                        clients.add(c)
                        statusCallback("گوشی دوم وصل شد. آماده مکالمه.")
                        thread(name = "Radio-Client-Reader") { readFromClient(c) }
                    }
                } catch (e: Exception) {
                    if (running.get()) statusCallback("خطای میزبان: ${e.message ?: "شبکه در دسترس نیست"}")
                }
            }
            thread(name = "Radio-Discovery") {
                try {
                    DatagramSocket(47654, InetAddress.getByName("0.0.0.0")).use { ds ->
                        ds.broadcast = true
                        val buf = ByteArray(128)
                        while (running.get()) {
                            val packet = DatagramPacket(buf, buf.size)
                            ds.receive(packet)
                            if (String(packet.data, packet.offset, packet.length, Charsets.UTF_8) == "MILADGEO_DISCOVER") {
                                val reply = DatagramPacket("MILADGEO_HOST".toByteArray(Charsets.UTF_8), 13, packet.address, packet.port)
                                ds.send(reply)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        fun findAndJoin() {
            statusCallback("در حال جست‌وجوی میزبان روی Wi-Fi محلی…")
            thread(name = "Radio-Find-Host") {
                var found: InetAddress? = null
                try {
                    DatagramSocket().use { ds ->
                        ds.broadcast = true
                        ds.soTimeout = 900
                        val requestBytes = "MILADGEO_DISCOVER".toByteArray(Charsets.UTF_8)
                        val targets = linkedSetOf<InetAddress>()
                        targets.add(InetAddress.getByName("255.255.255.255"))
                        // Also try the common Android hotspot subnet broadcast.
                        targets.add(InetAddress.getByName("192.168.43.255"))
                        targets.add(InetAddress.getByName("192.168.137.255"))
                        repeat(8) {
                            for (target in targets) {
                                try { ds.send(DatagramPacket(requestBytes, requestBytes.size, target, 47654)) } catch (_: Exception) {}
                            }
                            val buf = ByteArray(64)
                            try {
                                val response = DatagramPacket(buf, buf.size)
                                ds.receive(response)
                                if (String(response.data, response.offset, response.length, Charsets.UTF_8) == "MILADGEO_HOST") {
                                    found = response.address; return@use
                                }
                            } catch (_: java.net.SocketTimeoutException) {}
                        }
                    }
                } catch (_: Exception) {}
                val address = found
                if (address == null) {
                    statusCallback("میزبان پیدا نشد. مطمئن شو هات‌اسپات روشن است و هر دو گوشی به همان شبکه وصل‌اند؛ ممکن است هات‌اسپات پخش‌یابی را مسدود کند.")
                    return@thread
                }
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.connect(java.net.InetSocketAddress(address, 47655), 3000)
                    clientSocket = s
                    connected = true
                    statusCallback("به میزبان وصل شدی؛ دکمه صحبت را نگه دار.")
                    val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                    while (running.get() && !s.isClosed) {
                        val len = try { input.readInt() } catch (_: Exception) { break }
                        if (len in 1..4096) {
                            val data = ByteArray(len); input.readFully(data); playAudio(data)
                        } else break
                    }
                    connected = false
                    if (running.get()) statusCallback("ارتباط با میزبان قطع شد.")
                } catch (e: Exception) {
                    connected = false
                    statusCallback("اتصال ناموفق بود: ${e.message ?: "خطای شبکه"}")
                }
            }
        }

        private fun readFromClient(client: Client) {
            try {
                while (running.get() && !client.socket.isClosed) {
                    val len = client.input.readInt()
                    if (len !in 1..4096) break
                    val data = ByteArray(len); client.input.readFully(data)
                    playAudio(data)
                    for (other in clients) if (other !== client) other.send(data)
                }
            } catch (_: Exception) {
            } finally {
                clients.remove(client); client.close()
                if (running.get()) statusCallback("یک گوشی از ارتباط خارج شد.")
            }
        }

        fun sendAudio(bytes: ByteArray) {
            if (!connected) return
            if (hostMode) {
                for (c in clients) c.send(bytes)
            } else {
                val s = clientSocket ?: return
                try { synchronized(s) { val out = DataOutputStream(BufferedOutputStream(s.getOutputStream())); out.writeInt(bytes.size); out.write(bytes); out.flush() } } catch (_: Exception) { }
            }
        }
        fun close() {
            running.set(false); connected = false
            try { server?.close() } catch (_: Exception) {}
            try { clientSocket?.close() } catch (_: Exception) {}
            clients.forEach { it.close() }; clients.clear()
        }
        private inner class Client(val socket: Socket) {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            @Synchronized fun send(data: ByteArray) {
                try { output.writeInt(data.size); output.write(data); output.flush() } catch (_: Exception) {}
            }
            fun close() { try { socket.close() } catch (_: Exception) {} }
        }
    }
}
