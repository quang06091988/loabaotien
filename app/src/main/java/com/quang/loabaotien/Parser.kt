package com.quang.loabaotien

import java.text.Normalizer
import java.util.Locale

/** Một giao dịch tiền vào tách được từ thông báo. durable = có mã tham chiếu/thời gian để chống đọc trùng. */
data class Tx(val amount: Long, val key: String, val durable: Boolean)

object Parser {
    // Sau dấu ":" cho phép ký tự lạ (xuống dòng, dấu ```, emoji, dấu +) trước khi tới giá trị.
    // Văn bản đã được normalize nên mọi chữ cái đều là a-z; [^0-9a-z] chỉ gồm ký tự không phải chữ/số.
    private val reAmount = Regex("^\\s*:[^0-9a-z]{0,40}?([0-9][0-9.,]*)")
    private val reRef = Regex("ma tham chieu\\s*:[^0-9a-z]{0,40}?([0-9a-z\\-]*[0-9][0-9a-z\\-]*)")
    private val reTime = Regex("giao dich luc\\s*:[^0-9a-z]{0,40}?([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9:]{8})")

    /** Bỏ dấu, hạ chữ thường: "Tiền vào" -> "tien vao". */
    fun normalize(s: String): String {
        val d = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return d.replace('đ', 'd').replace('Đ', 'd').lowercase(Locale.ROOT)
    }

    /** Tách mọi "Tiền vào: X" trong văn bản (có thể chứa nhiều tin gộp lại). Chỉ lấy số tiền > 0. */
    fun parse(full: String): List<Tx> {
        val norm = normalize(full)
        val out = mutableListOf<Tx>()
        val pieces = norm.split("tien vao").drop(1)
        for (seg in pieces) {
            val m = reAmount.find(seg) ?: continue
            val amount = m.groupValues[1].filter { it.isDigit() }.toLongOrNull() ?: continue
            if (amount <= 0L) continue
            val ref = reRef.find(seg)?.groupValues?.get(1)
            val time = reTime.find(seg)?.groupValues?.get(1)
            val key = when {
                ref != null -> "r:$ref"
                time != null -> "t:$time:$amount"
                else -> "a:$amount"
            }
            out.add(Tx(amount, key, ref != null || time != null))
        }
        return out
    }
}
