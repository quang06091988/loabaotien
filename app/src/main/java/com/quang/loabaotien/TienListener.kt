package com.quang.loabaotien

import android.app.Notification
import android.content.ComponentName
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lắng nghe thông báo Telegram, thấy tin SePay "Tiền vào" thì đọc to số tiền. */
class TienListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()
    private val seen = LinkedHashMap<String, Long>()

    private val telegramPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "nekox.messenger"
    )

    private fun clock(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun diag(k: String, v: String) = Prefs.diagSet(applicationContext, k, v)

    override fun onCreate() {
        super.onCreate()
        diag("svc", "Dịch vụ được tạo lúc ${clock()}")
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onDestroy() {
        diag("conn", "Dịch vụ bị dừng lúc ${clock()}")
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            diag("tts", "LỖI khởi động giọng đọc trong dịch vụ (mã $status) lúc ${clock()}")
            return
        }
        val r = t.setLanguage(Locale("vi", "VN"))
        val viOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        diag("tts", "Sẵn sàng lúc ${clock()}, tiếng Việt: " + if (viOk) "có" else "THIẾU")
        while (pending.isNotEmpty()) speak(pending.removeAt(0))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        diag("conn", "Đã kết nối lúc ${clock()}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        diag("conn", "Mất kết nối lúc ${clock()}, đang thử kết nối lại")
        NotificationListenerService.requestRebind(ComponentName(this, TienListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        diag("any", "${clock()} từ ${sbn.packageName}")
        main.post { handle(sbn) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val ctx = applicationContext
        if (sbn.packageName !in telegramPackages) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val e = n.extras
        val parts = mutableListOf<String>()
        e.getCharSequence(Notification.EXTRA_TITLE)?.let { parts.add(it.toString()) }
        e.getCharSequence(Notification.EXTRA_TEXT)?.let { parts.add(it.toString()) }
        e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts.add(it.toString()) }
        e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts.add(it.toString()) }
        @Suppress("DEPRECATION")
        e.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach {
            (it as? Bundle)?.getCharSequence("text")?.let { t -> parts.add(t.toString()) }
        }
        val full = parts.joinToString("\n")

        val head = "${clock()} · ${sbn.packageName}\n" +
            "Nội dung: " + full.replace('\n', ' ').take(160) + "\n"
        fun rec(s: String) = Prefs.diagSet(ctx, "tg", head + "Kết quả: " + s)

        if (!Prefs.enabled(ctx)) {
            rec("bỏ qua vì công tắc \"Bật đọc tiền\" đang tắt")
            return
        }

        val kw = Parser.normalize(Prefs.keyword(ctx).trim())
        if (kw.isNotEmpty() && !Parser.normalize(full).contains(kw)) {
            rec("bỏ qua vì nội dung không chứa từ khóa \"${Prefs.keyword(ctx).trim()}\"")
            return
        }

        val txs = Parser.parse(full)
        if (txs.isEmpty()) {
            rec("bỏ qua vì không tìm thấy dạng \"Tiền vào: <số>\"")
            return
        }

        val now = System.currentTimeMillis()
        cleanup(now)
        val durableAmounts = txs.filter { it.durable }.map { it.amount }.toSet()
        val spoken = mutableListOf<Long>()
        var duplicates = 0

        for (tx in txs) {
            if (!tx.durable && tx.amount in durableAmounts) continue
            if (tx.durable) {
                if (seen.containsKey(tx.key)) { duplicates++; continue }
            } else {
                val last = seen["a:${tx.amount}"]
                if (last != null && now - last < 120_000L) { duplicates++; continue }
            }
            seen[tx.key] = now
            seen["a:${tx.amount}"] = now
            spoken.add(tx.amount)
            speak("Đã nhận " + VietnameseNumber.toWords(tx.amount) + " đồng")
        }

        when {
            spoken.isNotEmpty() -> rec("đã gửi đọc " + spoken.joinToString(", ") + " đồng" +
                if (ready) "" else " (giọng đọc chưa sẵn sàng, đang chờ)")
            duplicates > 0 -> rec("bỏ qua vì trùng với tin đã đọc (cùng số trong 2 phút)")
            else -> rec("không có gì để đọc")
        }
    }

    private fun cleanup(now: Long) {
        seen.entries.removeIf { now - it.value > 86_400_000L }
        if (seen.size > 500) seen.clear()
    }

    private fun speak(text: String) {
        val t = tts
        if (!ready || t == null) {
            pending.add(text)
            return
        }
        t.setSpeechRate(Prefs.rate(applicationContext))
        val r = t.speak(text, TextToSpeech.QUEUE_ADD, null, "tx-" + System.nanoTime())
        if (r != TextToSpeech.SUCCESS) {
            diag("tts", "LỖI khi phát tiếng (mã $r) lúc ${clock()}")
        }
    }
}
