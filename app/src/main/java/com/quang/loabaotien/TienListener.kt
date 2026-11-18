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

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        t.setLanguage(Locale("vi", "VN"))
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        while (pending.isNotEmpty()) speak(pending.removeAt(0))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationListenerService.requestRebind(ComponentName(this, TienListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        main.post { handle(sbn) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val ctx = applicationContext
        if (!Prefs.enabled(ctx)) return
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

        val kw = Parser.normalize(Prefs.keyword(ctx).trim())
        if (kw.isNotEmpty() && !Parser.normalize(full).contains(kw)) return

        val txs = Parser.parse(full)
        if (txs.isEmpty()) return

        val now = System.currentTimeMillis()
        cleanup(now)
        val durableAmounts = txs.filter { it.durable }.map { it.amount }.toSet()

        for (tx in txs) {
            if (!tx.durable && tx.amount in durableAmounts) continue
            if (tx.durable) {
                if (seen.containsKey(tx.key)) continue
            } else {
                val last = seen["a:${tx.amount}"]
                if (last != null && now - last < 120_000L) continue
            }
            seen[tx.key] = now
            seen["a:${tx.amount}"] = now
            speak("Đã nhận " + VietnameseNumber.toWords(tx.amount) + " đồng")
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
        t.speak(text, TextToSpeech.QUEUE_ADD, null, "tx-" + System.nanoTime())
    }
}
