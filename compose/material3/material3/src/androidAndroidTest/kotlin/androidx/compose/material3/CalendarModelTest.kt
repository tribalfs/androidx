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
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
@OptIn(ExperimentalMaterial3Api::class)
internal class CalendarModelTest(private val model: CalendarModel) {

    private lateinit var defaultLocale: Locale

    @Before
    fun before() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun after() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun dateCreation() {
        val date = model.getDate(January2022Millis) // 1/1/2022
        assertThat(date.year).isEqualTo(2022)
        assertThat(date.month).isEqualTo(1)
        assertThat(date.dayOfMonth).isEqualTo(1)
        assertThat(date.utcTimeMillis).isEqualTo(January2022Millis)
    }

    @Test
    fun dateRestore() {
        val date =
            CalendarDate(year = 2022, month = 1, dayOfMonth = 1, utcTimeMillis = January2022Millis)
        assertThat(model.getDate(date.utcTimeMillis)).isEqualTo(date)
    }

    @Test
    fun monthCreation() {
        val date =
            CalendarDate(year = 2022, month = 1, dayOfMonth = 1, utcTimeMillis = January2022Millis)
        val monthFromDate = model.getMonth(date)
        val monthFromMilli = model.getMonth(January2022Millis)
        val monthFromYearMonth = model.getMonth(year = 2022, month = 1)
        assertThat(monthFromDate).isEqualTo(monthFromMilli)
        assertThat(monthFromDate).isEqualTo(monthFromYearMonth)
    }

    @Test
    fun monthRestore() {
        val month = model.getMonth(year = 1999, month = 12)
        assertThat(model.getMonth(month.startUtcTimeMillis)).isEqualTo(month)
    }

    @Test
    fun plusMinusMonth() {
        val month = model.getMonth(January2022Millis) // 1/1/2022
        val expectedNextMonth = model.getMonth(month.endUtcTimeMillis + 1) // 2/1/2022
        val plusMonth = model.plusMonths(from = month, addedMonthsCount = 1)
        assertThat(plusMonth).isEqualTo(expectedNextMonth)
        assertThat(model.minusMonths(from = plusMonth, subtractedMonthsCount = 1)).isEqualTo(month)
    }

    @Test
    fun parseDate() {
        val expectedDate =
            CalendarDate(year = 2022, month = 1, dayOfMonth = 1, utcTimeMillis = January2022Millis)
        val parsedDate = model.parse("1/1/2022", "M/d/yyyy")
        assertThat(parsedDate).isEqualTo(expectedDate)
    }

    @Test
    fun formatDate() {
        val date =
            CalendarDate(year = 2022, month = 1, dayOfMonth = 1, utcTimeMillis = January2022Millis)
        val month = model.plusMonths(model.getMonth(date), 2)
        assertThat(model.format(date, "MM/dd/yyyy")).isEqualTo("01/01/2022")
        assertThat(model.format(month, "MM/dd/yyyy")).isEqualTo("03/01/2022")
    }

    @Test
    fun weekdayNames() {
        // Ensure we are running on a US locale for this test.
        Locale.setDefault(Locale.US)
        val weekDays = model.weekdayNames
        assertThat(weekDays).hasSize(DaysInWeek)
        // Check that the first day is always "Monday", per ISO-8601 standard.
        assertThat(weekDays.first().first).ignoringCase().contains("Monday")
        weekDays.forEach {
            assertThat(it.second.first().lowercaseChar()).isEqualTo(
                it.first.first().lowercaseChar()
            )
        }
    }

    @Test
    fun dateInputFormat() {
        Locale.setDefault(Locale.US)
        assertThat(model.dateInputFormat.patternWithDelimiters).isEqualTo("MM/dd/yyyy")
        assertThat(model.dateInputFormat.patternWithoutDelimiters).isEqualTo("MMddyyyy")
        assertThat(model.dateInputFormat.delimiter).isEqualTo('/')

        Locale.setDefault(Locale.CHINA)
        assertThat(model.dateInputFormat.patternWithDelimiters).isEqualTo("yyyy/MM/dd")
        assertThat(model.dateInputFormat.patternWithoutDelimiters).isEqualTo("yyyyMMdd")
        assertThat(model.dateInputFormat.delimiter).isEqualTo('/')

        Locale.setDefault(Locale.UK)
        assertThat(model.dateInputFormat.patternWithDelimiters).isEqualTo("dd/MM/yyyy")
        assertThat(model.dateInputFormat.patternWithoutDelimiters).isEqualTo("ddMMyyyy")
        assertThat(model.dateInputFormat.delimiter).isEqualTo('/')

        Locale.setDefault(Locale.KOREA)
        assertThat(model.dateInputFormat.patternWithDelimiters).isEqualTo("yyyy.MM.dd")
        assertThat(model.dateInputFormat.patternWithoutDelimiters).isEqualTo("yyyyMMdd")
        assertThat(model.dateInputFormat.delimiter).isEqualTo('.')

        Locale.setDefault(Locale("es", "CL"))
        assertThat(model.dateInputFormat.patternWithDelimiters).isEqualTo("dd-MM-yyyy")
        assertThat(model.dateInputFormat.patternWithoutDelimiters).isEqualTo("ddMMyyyy")
        assertThat(model.dateInputFormat.delimiter).isEqualTo('-')
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun equalModelsOutput() {
        // Note: This test ignores the parameters and just runs a few equality tests for the output.
        // It will execute twice, but that should to tolerable :)
        val newModel = CalendarModelImpl()
        val legacyModel = LegacyCalendarModelImpl()

        val date = newModel.getDate(January2022Millis) // 1/1/2022
        val legacyDate = legacyModel.getDate(January2022Millis)
        val month = newModel.getMonth(date)
        val legacyMonth = legacyModel.getMonth(date)

        assertThat(newModel.today).isEqualTo(legacyModel.today)
        assertThat(month).isEqualTo(legacyMonth)
        assertThat(newModel.dateInputFormat).isEqualTo(legacyModel.dateInputFormat)
        assertThat(newModel.plusMonths(month, 3)).isEqualTo(legacyModel.plusMonths(month, 3))
        assertThat(date).isEqualTo(legacyDate)
        assertThat(newModel.getDayOfWeek(date)).isEqualTo(legacyModel.getDayOfWeek(date))
        assertThat(newModel.format(date, "MMM d, yyyy")).isEqualTo(
            legacyModel.format(
                date,
                "MMM d, yyyy"
            )
        )
        assertThat(newModel.format(month, "MMM yyyy")).isEqualTo(
            legacyModel.format(
                month,
                "MMM yyyy"
            )
        )
    }

    internal companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun parameters() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                arrayOf(
                    CalendarModelImpl(),
                    LegacyCalendarModelImpl()
                )
            } else {
                arrayOf(LegacyCalendarModelImpl())
            }
    }
}

private const val January2022Millis = 1640995200000
