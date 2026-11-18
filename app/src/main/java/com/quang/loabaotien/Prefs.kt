package com.quang.loabaotien

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun keyword(c: Context): String = sp(c).getString("keyword", "SePay") ?: ""
    fun setKeyword(c: Context, v: String) = sp(c).edit().putString("keyword", v).apply()

    fun rate(c: Context) = sp(c).getFloat("rate", 1.0f)
    fun setRate(c: Context, v: Float) = sp(c).edit().putFloat("rate", v).apply()

    /** Nhật ký chẩn đoán: dịch vụ ghi, màn hình chính đọc ra để hiển thị. */
    fun diagGet(c: Context, k: String): String = sp(c).getString("d_$k", "") ?: ""
    fun diagSet(c: Context, k: String, v: String) = sp(c).edit().putString("d_$k", v).apply()
}
