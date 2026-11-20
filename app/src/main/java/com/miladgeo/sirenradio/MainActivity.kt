package com.miladgeo.sirenradio

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var soundBoard: SoundBoard
    private lateinit var status: TextView
    private lateinit var radioView: RadioDeviceView
    private var pttActive = false
    private var masterVolume = 0.82f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundBoard = SoundBoard(this)
        window.statusBarColor = Color.rgb(10, 13, 18)
        window.navigationBarColor = Color.rgb(10, 13, 18)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 13, 18))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "SIREN SOUND APP"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(34)))

        val subtitle = TextView(this).apply {
            text = "پنل حرفه‌ای بی‌سیم و آژیر"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(173, 184, 198))
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, dp(28)))

        status = TextView(this).apply {
            text = "●  آماده به کار  |  کانال ۰۵"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(102, 224, 173))
            background = rounded(Color.rgb(23, 43, 43), dp(18))
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val statusParams = LinearLayout.LayoutParams(-1, dp(38))
        statusParams.setMargins(dp(8), dp(8), dp(8), dp(14))
        root.addView(status, statusParams)

        val board = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val leftPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        leftPanel.addView(panelHeading("بی‌سیم"))
        addTile(leftPanel, "◉\nPUSH TO TALK\nنگه دار و صحبت کن", Color.rgb(20, 91, 91)) {
            // A click is intentionally not used for PTT. It is a press-and-hold control.
            setStatus("برای ارسال افکت، دکمه را نگه دار")
        }.apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startPtt(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopPtt(); true }
                    else -> true
                }
            }
        }
        addTile(leftPanel, "⏻\nRADIO START\nروشن کردن", Color.rgb(40, 65, 86)) {
            playOneShot("walkie_start", "صدای روشن شدن بی‌سیم")
        }
        addTile(leftPanel, "〰\nRADIO STATIC\nنویز کانال", Color.rgb(44, 58, 75)) {
            toggleLoop("radio_interference", "نویز واقعی کانال بی‌سیم")
        }
        addTile(leftPanel, "⌨\nKEYPAD\nصدای کلیدها", Color.rgb(44, 58, 75)) {
            playOneShot("walkie_keypad", "صدای کلیدهای بی‌سیم")
        }

        radioView = RadioDeviceView(this)
        val centerParams = LinearLayout.LayoutParams(0, dp(430), 1.48f).apply {
            setMargins(dp(4), dp(24), dp(4), 0)
        }
        board.addView(leftPanel, LinearLayout.LayoutParams(0, -2, 1f))
        board.addView(radioView, centerParams)

        val rightPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        rightPanel.addView(panelHeading("آژیرها"))
        addTile(rightPanel, "🚨\nPOLICE WAIL\nآژیر پلیس", Color.rgb(104, 38, 48)) {
            toggleLoop("police_siren_fr_outdoor", "آژیر واقعی پلیس")
        }
        addTile(rightPanel, "🚔\nPOLICE PASS\nپلیس عبوری", Color.rgb(81, 48, 48)) {
            playOneShot("police_siren_fr_passing", "عبور خودروی پلیس")
        }
        addTile(rightPanel, "🚑\nAMBULANCE\nآمبولانس", Color.rgb(38, 76, 90)) {
            toggleLoop("ambulance_siren_outdoor", "آژیر آمبولانس")
        }
        addTile(rightPanel, "🚑\nAMBULANCE +\nصدای موتور", Color.rgb(38, 76, 90)) {
            toggleLoop("ambulance_siren_engine", "آژیر آمبولانس همراه موتور")
        }
        addTile(rightPanel, "📢\nCITY ALERT\nهشدار شهری", Color.rgb(94, 67, 31)) {
            toggleLoop("national_alert_siren", "آژیر هشدار شهری")
        }
        addTile(rightPanel, "🏍\nMOTOR SIREN\nموتورسیکلت", Color.rgb(81, 48, 48)) {
            playOneShot("gendarmerie_motorcycle_siren", "عبور موتورسیکلت آژیر‌دار")
        }
        board.addView(rightPanel, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(board)

        val volumeLabel = TextView(this).apply {
            text = "MASTER VOLUME  •  بلندی صدای اصلی"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(166, 190, 208))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(2))
        }
        root.addView(volumeLabel)
        root.addView(SeekBar(this).apply {
            max = 100
            progress = (masterVolume * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    masterVolume = progress / 100f
                    soundBoard.setVolume(masterVolume)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))

        val footer = TextView(this).apply {
            text = "صداهای ضبط‌شده • پخش آفلاین • کنترل مستقل افکت‌ها"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(112, 126, 143))
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(footer)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun panelHeading(label: String) = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(209, 220, 230))
        setPadding(0, 0, 0, dp(8))
    }

    private fun addTile(parent: LinearLayout, label: String, color: Int, action: () -> Unit): TextView {
        val tile = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 10.5f
            maxLines = 3
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(color, dp(12)).apply {
                setStroke(dp(1), Color.argb(55, 220, 235, 250))
            }
            setPadding(dp(3), dp(7), dp(3), dp(7))
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(-1, dp(65))
        params.setMargins(dp(2), dp(2), dp(2), dp(7))
        parent.addView(tile, params)
        return tile
    }

    private fun toggleLoop(id: String, label: String) {
        if (soundBoard.isPlaying(id)) {
            soundBoard.stop(id)
            setStatus("$label متوقف شد")
        } else if (soundBoard.play(id, true)) {
            setStatus("$label در حال پخش است")
        } else {
            setStatus("فایل صوتی $label در بسته برنامه پیدا نشد")
        }
    }

    private fun playOneShot(id: String, label: String) {
        if (soundBoard.play(id, false)) setStatus("$label در حال پخش است")
        else setStatus("فایل صوتی $label پیدا نشد")
    }

    private fun startPtt() {
        if (pttActive) return
        pttActive = true
        radioView.setTransmitting(true)
        soundBoard.play("radio_interference", false)
        if (soundBoard.play("walkie_conversation", true)) {
            setStatus("●  در حال ارسال افکت بی‌سیم  |  PTT")
        } else {
            setStatus("فایل مکالمه بی‌سیم پیدا نشد")
        }
    }

    private fun stopPtt() {
        if (!pttActive) return
        pttActive = false
        soundBoard.stop("walkie_conversation")
        soundBoard.play("radio_interference", false)
        radioView.setTransmitting(false)
        setStatus("●  آماده به کار  |  کانال ۰۵")
    }

    private fun setStatus(value: String) {
        status.text = value
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        soundBoard.release()
        super.onDestroy()
    }
}

private class SoundBoard(context: Context) {
    private val appContext = context.applicationContext
    private val players = mutableMapOf<String, MediaPlayer>()
    @Volatile private var volume = 0.82f

    fun isPlaying(id: String) = players.containsKey(id)

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        players.values.forEach { player ->
            try { player.setVolume(volume, volume) } catch (_: Exception) {}
        }
    }

    fun play(id: String, loop: Boolean): Boolean {
        stop(id)
        val resourceId = appContext.resources.getIdentifier(id, "raw", appContext.packageName)
        if (resourceId == 0) return false
        val player = try { MediaPlayer.create(appContext, resourceId) } catch (_: Exception) { null }
            ?: return false

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.isLooping = loop
        player.setVolume(volume, volume)
        players[id] = player
        player.setOnCompletionListener { finished ->
            if (players[id] === finished) players.remove(id)
            try { finished.release() } catch (_: Exception) {}
        }
        return try {
            player.start()
            true
        } catch (_: Exception) {
            players.remove(id)
            try { player.release() } catch (_: Exception) {}
            false
        }
    }

    fun stop(id: String) {
        val player = players.remove(id) ?: return
        try {
            player.setOnCompletionListener(null)
            if (player.isPlaying) player.stop()
        } catch (_: Exception) {
        } finally {
            try { player.release() } catch (_: Exception) {}
        }
    }

    fun stopAll() {
        players.keys.toList().forEach { stop(it) }
    }

    fun release() {
        stopAll()
    }
}

private class RadioDeviceView(context: Context) : View(context) {
    private var transmitting = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            22f, 100f, 218f, 390f,
            intArrayOf(Color.rgb(20, 23, 29), Color.rgb(58, 62, 70), Color.rgb(13, 15, 20)),
            null,
            Shader.TileMode.CLAMP
        )
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun setTransmitting(value: Boolean) {
        transmitting = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 240f, height / 440f)
        val offsetX = (width - 240f * scale) / 2f
        val offsetY = (height - 440f * scale) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Antenna and top controls.
        paint.color = Color.rgb(9, 11, 15)
        paint.strokeWidth = 13f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(151f, 105f, 151f, 15f, paint)
        paint.color = Color.rgb(104, 111, 122)
        paint.strokeWidth = 4f
        canvas.drawLine(151f, 104f, 151f, 17f, paint)
        paint.color = Color.rgb(16, 19, 24)
        canvas.drawCircle(151f, 16f, 8f, paint)

        paint.color = Color.rgb(13, 15, 20)
        canvas.drawRoundRect(RectF(43f, 70f, 85f, 112f), 10f, 10f, paint)
        canvas.drawRoundRect(RectF(94f, 70f, 136f, 112f), 10f, 10f, paint)
        paint.color = Color.rgb(90, 98, 110)
        canvas.drawCircle(64f, 88f, 13f, paint)
        canvas.drawCircle(115f, 88f, 13f, paint)
        paint.color = Color.rgb(26, 30, 37)
        canvas.drawCircle(64f, 88f, 8f, paint)
        canvas.drawCircle(115f, 88f, 8f, paint)

        // Radio body.
        paint.color = Color.rgb(0, 0, 0)
        paint.setShadowLayer(18f, 0f, 10f, Color.argb(160, 0, 0, 0))
        canvas.drawRoundRect(RectF(22f, 99f, 218f, 409f), 30f, 30f, paint)
        paint.clearShadowLayer()
        canvas.drawRoundRect(RectF(28f, 105f, 212f, 403f), 25f, 25f, bodyPaint)

        // Speaker grille.
        paint.color = Color.rgb(18, 21, 27)
        canvas.drawRoundRect(RectF(45f, 126f, 195f, 255f), 13f, 13f, paint)
        paint.color = Color.rgb(102, 108, 118)
        paint.strokeWidth = 7f
        for (y in 145..235 step 23) {
            canvas.drawLine(62f, y.toFloat(), 178f, y.toFloat(), paint)
        }
        paint.color = Color.rgb(44, 49, 59)
        paint.strokeWidth = 2f
        for (x in 54..186 step 12) {
            canvas.drawLine(x.toFloat(), 135f, x.toFloat(), 245f, paint)
        }

        // LCD panel.
        paint.color = Color.rgb(5, 15, 15)
        canvas.drawRoundRect(RectF(51f, 268f, 189f, 324f), 9f, 9f, paint)
        paint.color = if (transmitting) Color.rgb(112, 255, 157) else Color.rgb(99, 219, 145)
        canvas.drawRoundRect(RectF(57f, 274f, 183f, 318f), 6f, 6f, paint)
        paint.color = Color.rgb(10, 40, 26)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("MILAD RADIO", 120f, 290f, paint)
        paint.textSize = 12f
        canvas.drawText(if (transmitting) "TRANSMITTING" else "CHANNEL 05", 120f, 307f, paint)

        // Status LED and keypad.
        paint.color = if (transmitting) Color.rgb(255, 67, 61) else Color.rgb(99, 220, 137)
        canvas.drawCircle(197f, 112f, 5f, paint)
        paint.color = Color.rgb(8, 10, 14)
        canvas.drawRoundRect(RectF(50f, 332f, 190f, 391f), 10f, 10f, paint)
        val buttons = arrayOf(
            74f to 349f, 120f to 349f, 166f to 349f,
            74f to 370f, 120f to 370f, 166f to 370f
        )
        buttons.forEachIndexed { index, point ->
            paint.color = if (index == 1) Color.rgb(66, 88, 111) else Color.rgb(45, 51, 61)
            canvas.drawRoundRect(RectF(point.first - 15f, point.second - 8f, point.first + 15f, point.second + 8f), 5f, 5f, paint)
            paint.color = Color.rgb(215, 224, 235)
            paint.textSize = 8f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText((index + 1).toString(), point.first, point.second + 3f, paint)
        }
        paint.color = Color.rgb(151, 160, 171)
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("UHF  •  DIGITAL RADIO", 120f, 400f, paint)
        canvas.restore()
    }
}
