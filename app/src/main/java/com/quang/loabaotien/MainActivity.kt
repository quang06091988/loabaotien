package com.quang.loabaotien

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var viOk = true
    private lateinit var status: TextView
    private lateinit var diagView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val diagTick = object : Runnable {
        override fun run() {
            refreshDiag()
            ui.postDelayed(this, 2000L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(label("Loa Báo Tiền", 26f, true))
        status = label("", 14f)
        root.addView(status)

        root.addView(button("1. Cấp quyền đọc thông báo") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        root.addView(Switch(this).apply {
            text = "Bật đọc tiền"
            textSize = 16f
            isChecked = Prefs.enabled(this@MainActivity)
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this@MainActivity, on) }
        })

        root.addView(label("Từ khóa lọc (tên nhóm hoặc bot trong Telegram):"))
        root.addView(EditText(this).apply {
            setText(Prefs.keyword(this@MainActivity))
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Prefs.setKeyword(this@MainActivity, s?.toString() ?: "")
                }
            })
        })

        val rateLabel = label("")
        fun showRate(r: Float) { rateLabel.text = "Tốc độ đọc: " + Math.round(r * 100) + "%" }
        showRate(Prefs.rate(this))
        root.addView(rateLabel)
        root.addView(SeekBar(this).apply {
            max = 100
            progress = Math.round((Prefs.rate(this@MainActivity) - 0.5f) * 100f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val r = 0.5f + p / 100f
                    Prefs.setRate(this@MainActivity, r)
                    showRate(r)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        root.addView(button("Nghe thử") { testVoice() })
        root.addView(button("Cài đặt giọng đọc (tiếng Việt)") {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        })
        root.addView(button("Tắt tối ưu pin cho app") {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        })
        root.addView(button("Mở cài đặt thông tin app") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        root.addView(button("Kết nối lại dịch vụ đọc thông báo") {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(this@MainActivity, TienListener::class.java)
                )
                Toast.makeText(this@MainActivity, "Đã gửi yêu cầu kết nối lại", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Lỗi: " + e.message, Toast.LENGTH_LONG).show()
            }
        })

        root.addView(label("Chẩn đoán (chụp màn hình phần này gửi người hỗ trợ)", 16f, true))
        diagView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(diagView)

        root.addView(label("Lưu ý", 16f, true))
        root.addView(
            TextView(this).apply {
                text = "• Trong Telegram: bật thông báo cho nhóm SePay, bật hiện nội dung tin nhắn (xem trước tin).\n" +
                    "• Đừng mở sẵn đúng nhóm đó trong Telegram, vì khi đó Telegram không gửi thông báo.\n" +
                    "• Máy Xiaomi/Oppo/Samsung: cho app tự khởi chạy và không giới hạn pin.\n" +
                    "• Máy cần có giọng đọc tiếng Việt (Google Text-to-speech)."
                textSize = 14f
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("vi", "VN"))
                viOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = true
            } else {
                ttsReady = false
            }
            refreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        ui.post(diagTick)
    }

    override fun onPause() {
        ui.removeCallbacks(diagTick)
        super.onPause()
    }

    private fun refreshDiag() {
        fun g(k: String) = Prefs.diagGet(this, k).ifEmpty { "(chưa có)" }
        diagView.text =
            "Quyền đọc thông báo: " + (if (listenerEnabled()) "ĐÃ BẬT" else "CHƯA BẬT") + "\n" +
            "Dịch vụ: " + g("svc") + "\n" +
            "Kết nối: " + g("conn") + "\n" +
            "Giọng đọc (dịch vụ): " + g("tts") + "\n" +
            "Thông báo bất kỳ gần nhất: " + g("any") + "\n" +
            "Thông báo Telegram gần nhất:\n" + g("tg")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(packageName)
    }

    private fun refreshStatus() {
        val a = if (listenerEnabled()) "✅ Đã cấp quyền đọc thông báo" else "⚠️ Chưa cấp quyền đọc thông báo"
        val b = when {
            !ttsReady -> "⏳ Đang kiểm tra giọng đọc..."
            viOk -> "✅ Máy có giọng đọc tiếng Việt"
            else -> "⚠️ Máy chưa có giọng tiếng Việt, hãy cài trong Cài đặt giọng đọc"
        }
        status.text = a + "\n" + b
    }

    private fun testVoice() {
        val t = tts
        if (!ttsReady || t == null) {
            Toast.makeText(this, "Giọng đọc chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }
        t.setSpeechRate(Prefs.rate(this))
        t.speak("Đã nhận " + VietnameseNumber.toWords(270500L) + " đồng", TextToSpeech.QUEUE_FLUSH, null, "test")
    }
}
