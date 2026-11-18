package com.example.i18n

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class AppLanguage(val code: String, val displayLabel: String) {
    FA("fa", "فارسی"),
    EN("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: EN
    }
}

object LocaleFormatter {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toLocalizedDigits(input: String, language: AppLanguage): String {
        if (language != AppLanguage.FA) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(value: Number, language: AppLanguage): String =
        toLocalizedDigits(value.toString(), language)

    fun formatDate(millis: Long, language: AppLanguage): String {
        if (millis <= 0L) return if (language == AppLanguage.FA) "نامشخص" else "Unknown"
        return if (language == AppLanguage.FA) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = millis }
            val (jy, jm, jd) = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val monthNames = arrayOf(
                "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
            )
            val formatted = "$jd ${monthNames[jm]} $jy"
            toLocalizedDigits(formatted, AppLanguage.FA)
        } else {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            sdf.format(Date(millis))
        }
    }

    fun formatDateTime(millis: Long, language: AppLanguage): String {
        if (millis <= 0L) return if (language == AppLanguage.FA) "نامشخص" else "Unknown"
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
        return if (language == AppLanguage.FA) {
            "${formatDate(millis, AppLanguage.FA)} • ${toLocalizedDigits(timeFmt, AppLanguage.FA)}"
        } else {
            "${formatDate(millis, AppLanguage.EN)} • $timeFmt"
        }
    }

    /**
     * Standard Gregorian to Jalali (Solar Hijri) calendar conversion.
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
        val jd = 1 + (if (days < 186) (days % 31) else ((days - 186) % 30))
        return Triple(jy, jm, jd)
    }
}
