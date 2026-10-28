package com.solina.feed

import java.util.Calendar

/** Gregorian to Jalali (Solar Hijri) conversion and Persian formatting helpers. */
object Jalali {

    data class Date(val year: Int, val month: Int, val day: Int)

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    // Index = Calendar.DAY_OF_WEEK % 7 (Saturday = 0).
    private val weekDays = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
    )

    private val cumulativeDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Date {
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 +
            gd + cumulativeDays[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Date(jy, jm, jd)
    }

    fun fromCalendar(c: Calendar): Date =
        fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    fun weekDayName(c: Calendar): String = weekDays[c.get(Calendar.DAY_OF_WEEK) % 7]

    /** For example: «چهارشنبه ۶ آبان ۱۴۰۵». */
    fun format(c: Calendar): String {
        val d = fromCalendar(c)
        return digits("${weekDayName(c)} ${d.day} ${monthNames[d.month - 1]} ${d.year}")
    }

    /** Replaces Latin digits with Persian digits. */
    fun digits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
        }
        return sb.toString()
    }
}

/** Fixed (solar-calendar) occasions. Lunar-calendar holidays are not included. */
object Occasions {

    data class Occasion(val title: String, val holiday: Boolean)

    private val fixed = mapOf(
        (1 to 1) to Occasion("جشن نوروز", true),
        (1 to 2) to Occasion("نوروز", true),
        (1 to 3) to Occasion("نوروز", true),
        (1 to 4) to Occasion("نوروز", true),
        (1 to 12) to Occasion("روز جمهوری اسلامی", true),
        (1 to 13) to Occasion("روز طبیعت (سیزده‌به‌در)", true),
        (2 to 1) to Occasion("روز بزرگداشت سعدی", false),
        (2 to 25) to Occasion("روز بزرگداشت فردوسی", false),
        (3 to 1) to Occasion("روز بزرگداشت عطار", false),
        (3 to 14) to Occasion("رحلت امام خمینی", true),
        (3 to 15) to Occasion("قیام ۱۵ خرداد", true),
        (4 to 1) to Occasion("روز اصناف", false),
        (7 to 8) to Occasion("روز بزرگداشت مولانا", false),
        (7 to 13) to Occasion("روز ملی کودک", false),
        (8 to 13) to Occasion("روز دانش‌آموز", false),
        (9 to 16) to Occasion("روز دانشجو", false),
        (9 to 30) to Occasion("شب یلدا", false),
        (11 to 22) to Occasion("پیروزی انقلاب اسلامی", true),
        (12 to 5) to Occasion("روز بزرگداشت خواجه نصیرالدین طوسی", false),
        (12 to 29) to Occasion("ملی شدن صنعت نفت", true),
    )

    fun of(d: Jalali.Date): Occasion? = fixed[d.month to d.day]
}
