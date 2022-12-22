/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A [CalendarModel] implementation for API >= 26.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
internal class CalendarModelImpl : CalendarModel {

    override val today
        get(): CalendarDate {
            val systemLocalDate = LocalDate.now()
            return CalendarDate(
                year = systemLocalDate.year,
                month = systemLocalDate.monthValue,
                dayOfMonth = systemLocalDate.dayOfMonth,
                utcTimeMillis = systemLocalDate.atTime(LocalTime.MIDNIGHT)
                    .atZone(utcTimeZoneId).toInstant().toEpochMilli()
            )
        }

    override val firstDayOfWeek: Int = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value

    override val weekdayNames: List<Pair<String, String>> =
        // This will start with Monday as the first day, according to ISO-8601.
        with(Locale.getDefault()) {
            DayOfWeek.values().map {
                it.getDisplayName(
                    TextStyle.FULL,
                    /* locale = */ this
                ) to it.getDisplayName(
                    TextStyle.NARROW,
                    /* locale = */ this
                )
            }
        }

    override val dateInputFormat: DateInputFormat
        get() = datePatternAsInputFormat(
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                /* dateStyle = */ FormatStyle.SHORT,
                /* timeStyle = */ null,
                /* chrono = */ Chronology.ofLocale(Locale.getDefault()),
                /* locale = */ Locale.getDefault()
            )
        )

    override fun getDate(timeInMillis: Long): CalendarDate {
        val localDate =
            Instant.ofEpochMilli(timeInMillis).atZone(utcTimeZoneId).toLocalDate()
        return CalendarDate(
            year = localDate.year,
            month = localDate.monthValue,
            dayOfMonth = localDate.dayOfMonth,
            utcTimeMillis = timeInMillis
        )
    }

    override fun getMonth(timeInMillis: Long): CalendarMonth {
        return getMonth(
            Instant.ofEpochMilli(timeInMillis).atZone(utcTimeZoneId).toLocalDate()
        )
    }

    override fun getMonth(date: CalendarDate): CalendarMonth {
        return getMonth(LocalDate.of(date.year, date.month, 1))
    }

    override fun getMonth(year: Int, month: Int): CalendarMonth {
        return getMonth(LocalDate.of(year, month, 1))
    }

    override fun getDayOfWeek(date: CalendarDate): Int {
        return date.toLocalDate().dayOfWeek.value
    }

    override fun plusMonths(from: CalendarMonth, addedMonthsCount: Int): CalendarMonth {
        if (addedMonthsCount <= 0) return from

        val firstDayLocalDate = from.toLocalDate()
        val laterMonth = firstDayLocalDate.plusMonths(addedMonthsCount.toLong())
        return getMonth(laterMonth)
    }

    override fun minusMonths(from: CalendarMonth, subtractedMonthsCount: Int): CalendarMonth {
        if (subtractedMonthsCount <= 0) return from

        val firstDayLocalDate = from.toLocalDate()
        val earlierMonth = firstDayLocalDate.minusMonths(subtractedMonthsCount.toLong())
        return getMonth(earlierMonth)
    }

    override fun format(month: CalendarMonth, pattern: String): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)
        return month.toLocalDate().format(formatter)
    }

    override fun format(date: CalendarDate, pattern: String): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)
        return date.toLocalDate().format(formatter)
    }

    override fun parse(date: String, pattern: String): CalendarDate? {
        // TODO: A DateTimeFormatter can be reused.
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return try {
            val localDate = LocalDate.parse(date, formatter)
            CalendarDate(
                year = localDate.year,
                month = localDate.month.value,
                dayOfMonth = localDate.dayOfMonth,
                utcTimeMillis = localDate.atTime(LocalTime.MIDNIGHT)
                    .atZone(utcTimeZoneId).toInstant().toEpochMilli()
            )
        } catch (pe: DateTimeParseException) {
            null
        }
    }

    private fun getMonth(firstDayLocalDate: LocalDate): CalendarMonth {
        val difference = firstDayLocalDate.dayOfWeek.value - firstDayOfWeek
        val daysFromStartOfWeekToFirstOfMonth = if (difference < 0) {
            difference + DaysInWeek
        } else {
            difference
        }
        val firstDayEpochMillis =
            firstDayLocalDate.atTime(LocalTime.MIDNIGHT).atZone(utcTimeZoneId).toInstant()
                .toEpochMilli()
        return CalendarMonth(
            year = firstDayLocalDate.year,
            month = firstDayLocalDate.monthValue,
            numberOfDays = firstDayLocalDate.lengthOfMonth(),
            daysFromStartOfWeekToFirstOfMonth = daysFromStartOfWeekToFirstOfMonth,
            startUtcTimeMillis = firstDayEpochMillis
        )
    }

    private fun CalendarMonth.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(startUtcTimeMillis).atZone(utcTimeZoneId).toLocalDate()
    }

    private fun CalendarDate.toLocalDate(): LocalDate {
        return LocalDate.of(
            this.year,
            this.month,
            this.dayOfMonth
        )
    }

    private val utcTimeZoneId = ZoneId.of("UTC")
}
