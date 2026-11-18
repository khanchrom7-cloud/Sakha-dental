package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarMode {
    SOLAR_HIJRI,
    GREGORIAN,
    DUAL
}

data class SolarHijriDate(
    val year: Int,
    val month: Int,
    val day: Int
) {
    fun toNumericString(): String = "%04d/%02d/%02d".format(Locale.US, year, month, day)

    fun formatLocalized(language: AppLanguage): String {
        val monthName = when (language) {
            AppLanguage.DARI -> DARI_MONTHS.getOrElse(month - 1) { month.toString() }
            AppLanguage.PASHTO -> PASHTO_MONTHS.getOrElse(month - 1) { month.toString() }
            AppLanguage.ENGLISH -> EN_MONTHS.getOrElse(month - 1) { month.toString() }
        }
        return "$day $monthName $year"
    }

    companion object {
        val DARI_MONTHS = listOf(
            "حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله",
            "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت"
        )
        val PASHTO_MONTHS = listOf(
            "وری", "غویی", "غبرګولی", "چنګاښ", "زمری", "وږی",
            "تله", "لړم", "لیندۍ", "مرغومی", "سلواغه", "کب"
        )
        val EN_MONTHS = listOf(
            "Hamal", "Saur", "Jawza", "Saratan", "Asad", "Sunbula",
            "Mizan", "Aqrab", "Qaws", "Jadi", "Dalwa", "Hoot"
        )
    }
}

object ClinicCalendar {

    fun gregorianToSolarHijri(gy: Int, gm: Int, gd: Int): SolarHijriDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
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
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return SolarHijriDate(jy, jm, jd)
    }

    fun fromIsoDate(dateIso: String): SolarHijriDate {
        val parts = dateIso.trim().split("-")
        if (parts.size != 3) return todaySolarHijri()
        val y = parts[0].toIntOrNull() ?: return todaySolarHijri()
        val m = parts[1].toIntOrNull() ?: return todaySolarHijri()
        val d = parts[2].toIntOrNull() ?: return todaySolarHijri()
        return gregorianToSolarHijri(y, m, d)
    }

    fun todayIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date())
    }

    fun todaySolarHijri(): SolarHijriDate {
        val cal = Calendar.getInstance()
        return gregorianToSolarHijri(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDate(dateIso: String, mode: CalendarMode, language: AppLanguage): String {
        val sh = fromIsoDate(dateIso)
        return when (mode) {
            CalendarMode.SOLAR_HIJRI -> sh.formatLocalized(language)
            CalendarMode.GREGORIAN -> dateIso
            CalendarMode.DUAL -> "${sh.formatLocalized(language)} ($dateIso)"
        }
    }

    fun format12HourTime(hour24: Int, minute: Int, language: AppLanguage): String {
        val isPm = hour24 >= 12
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val suffix = when (language) {
            AppLanguage.DARI -> if (isPm) "ب.ظ" else "ق.ظ"
            AppLanguage.PASHTO -> if (isPm) "م.و" else "غ.م"
            AppLanguage.ENGLISH -> if (isPm) "PM" else "AM"
        }
        return "%d:%02d %s".format(Locale.US, hour12, minute, suffix)
    }

    fun generateDefaultTimeSlots(language: AppLanguage): List<String> {
        val slots = mutableListOf<String>()
        for (hour in 8..18) {
            slots += format12HourTime(hour, 0, language)
            slots += format12HourTime(hour, 30, language)
        }
        return slots
    }
}
