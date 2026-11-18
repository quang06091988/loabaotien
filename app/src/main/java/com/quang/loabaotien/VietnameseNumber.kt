package com.quang.loabaotien

/** Đổi số tiền thành chữ tiếng Việt: 1050000 -> "một triệu không trăm năm mươi nghìn". */
object VietnameseNumber {
    private val digits = arrayOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")

    fun toWords(n: Long): String {
        if (n <= 0L) return "không"
        if (n >= 1_000_000_000L) {
            val high = n / 1_000_000_000L
            val rest = n % 1_000_000_000L
            val head = toWords(high) + " tỷ"
            return if (rest == 0L) head else head + " " + belowBillion(rest, true)
        }
        return belowBillion(n, false)
    }

    private fun belowBillion(n: Long, hasHigher: Boolean): String {
        val units = arrayOf("triệu", "nghìn", "")
        val groups = intArrayOf(
            (n / 1_000_000L % 1000L).toInt(),
            (n / 1000L % 1000L).toInt(),
            (n % 1000L).toInt()
        )
        val parts = mutableListOf<String>()
        var printed = hasHigher
        for (i in 0..2) {
            val g = groups[i]
            if (g == 0) continue
            val words = read3(g, printed)
            parts.add(if (units[i].isEmpty()) words else words + " " + units[i])
            printed = true
        }
        return parts.joinToString(" ")
    }

    private fun read3(g: Int, full: Boolean): String {
        val h = g / 100
        val t = g / 10 % 10
        val u = g % 10
        val sb = mutableListOf<String>()
        if (h > 0 || full) sb.add(digits[h] + " trăm")
        if (t > 1) {
            sb.add(digits[t] + " mươi")
            if (u == 1) sb.add("mốt") else if (u == 5) sb.add("lăm") else if (u > 0) sb.add(digits[u])
        } else if (t == 1) {
            sb.add("mười")
            if (u == 5) sb.add("lăm") else if (u > 0) sb.add(digits[u])
        } else if (u > 0) {
            if (h > 0 || full) sb.add("linh")
            sb.add(digits[u])
        }
        return sb.joinToString(" ")
    }
}
