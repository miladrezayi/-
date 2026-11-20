package com.miladgeo.sirenradio

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : Activity() {
    private val engine = SoundEngine()
    private lateinit var status: TextView
    private var master = 75

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 29)
        window.navigationBarColor = Color.rgb(12, 18, 29)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 26, 22, 20)
            setBackgroundColor(Color.rgb(12, 18, 29))
        }
        root.addView(TextView(this).apply {
            text = "SIREN RADIO\nپنل بی‌سیم و آژیر"
            setTextColor(Color.WHITE); textSize = 25f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 8, 0, 22)
        })
        status = TextView(this).apply {
            text = "آماده به کار"; textSize = 14f
            setTextColor(Color.rgb(120, 220, 180)); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        root.addView(status)
        root.addView(section("بی‌سیم"))
        val ptt = makeButton("نگه دار و صحبت کن  •  PUSH TO TALK")
        ptt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { engine.start("ptt", 720.0, 960.0, true, master / 100f); status.text = "در حال ارسال افکت بی‌سیم…"; true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { engine.stop("ptt"); status.text = "آماده به کار"; true }
                else -> true
            }
        }
        root.addView(ptt)
        root.addView(section("آژیرها و هشدارها"))
        addSoundButton(root, "آژیر پلیس دو تُن", "police", 650.0, 880.0, true)
        addSoundButton(root, "آژیر پلیس متناوب", "euro", 520.0, 760.0, true)
        addSoundButton(root, "آژیر آتش‌نشانی", "fire", 430.0, 690.0, true)
        addSoundButton(root, "آژیر آمبولانس", "ambulance", 700.0, 1040.0, true)
        addSoundButton(root, "بوق هشدار", "warning", 880.0, 0.0, false)
        addSoundButton(root, "بوق بم", "low", 110.0, 0.0, false)
        root.addView(section("بلندی صدا"))
        root.addView(SeekBar(this).apply {
            max = 100; progress = master
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { master = p; engine.setVolume(p / 100f) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        val stop = makeButton("■  توقف همه صداها").apply {
            setBackgroundColor(Color.rgb(150, 42, 54))
            setOnClickListener { engine.stopAll(); status.text = "همه صداها متوقف شدند" }
        }
        root.addView(stop)
        setContentView(root)
    }

    private fun section(label: String) = TextView(this).apply {
        text = label; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(92, 190, 255)); gravity = Gravity.RIGHT
        setPadding(0, 16, 0, 8)
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(34, 53, 76))
        val p = (10 * resources.displayMetrics.density).toInt()
        setPadding(p, p, p, p)
    }

    private fun addSoundButton(root: LinearLayout, label: String, id: String, a: Double, b: Double, sweep: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val button = makeButton("▶  $label").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                if (engine.isPlaying(id)) { engine.stop(id); text = "▶  $label" }
                else { engine.start(id, a, b, sweep, master / 100f); text = "■  توقف $label" }
                status.text = "افکت‌ها می‌توانند هم‌زمان پخش شوند"
            }
        }
        row.addView(button)
        root.addView(row)
    }

    override fun onDestroy() { engine.stopAll(); super.onDestroy() }
}

private class SoundEngine {
    private val players = ConcurrentHashMap<String, AudioTrack>()
    private val workers = ConcurrentHashMap<String, Thread>()
    @Volatile private var volume = 0.75f

    fun isPlaying(id: String) = players.containsKey(id)
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f) }

    fun start(id: String, first: Double, second: Double, sweep: Boolean, gain: Float) {
        stop(id)
        val rate = 22050
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        players[id] = track
        track.play()
        val worker = Thread {
            val samples = ShortArray(1024)
            var phase = 0.0
            var elapsed = 0L
            try {
                while (!Thread.currentThread().isInterrupted && players[id] === track) {
                    for (i in samples.indices) {
                        val t = elapsed.toDouble() / rate
                        val freq = if (!sweep || second == 0.0) first else {
                            val cycle = (t % 1.2) / 1.2
                            first + (second - first) * (0.5 - 0.5 * kotlin.math.cos(2 * PI * cycle))
                        }
                        phase += 2 * PI * freq / rate
                        val modulation = if (id == "ptt") (0.72 + 0.28 * sin(2 * PI * 17 * t)) else 1.0
                        samples[i] = (sin(phase) * 15000 * gain * volume * modulation).toInt().toShort()
                        elapsed++
                    }
                    track.write(samples, 0, samples.size)
                }
            } catch (_: Exception) { }
            try { track.pause(); track.flush(); track.stop() } catch (_: Exception) {}
            track.release()
            players.remove(id, track)
            workers.remove(id)
        }
        workers[id] = worker
        worker.start()
    }

    fun stop(id: String) {
        workers[id]?.interrupt()
        try { players[id]?.pause() } catch (_: Exception) {}
        players.remove(id)
    }

    fun stopAll() { players.keys.toList().forEach { stop(it) } }
}
