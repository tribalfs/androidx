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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.tokens.DatePickerModalTokens
import androidx.compose.material3.tokens.MotionTokens
import androidx.compose.material3.tokens.TypographyKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.lang.Integer.max
import java.text.NumberFormat
import kotlinx.coroutines.launch

// TODO: External preview image.
// TODO: Update the docs to reference the upcoming DatePickerDialog.
/**
 * <a href="https://m3.material.io/components/date-pickers/overview" class="external" target="_blank">Material Design date picker</a>.
 *
 * Date pickers let people select a date and can be embedded into Dialogs.
 *
 * A simple DatePicker looks like:
 * @sample androidx.compose.material3.samples.DatePickerSample
 *
 * A DatePicker with validation that blocks certain days from being selected looks like:
 * @sample androidx.compose.material3.samples.DatePickerWithDateValidatorSample
 *
 * @param datePickerState state of the date picker. Use this state for pre-setting or obtaining the
 * selected date. See [rememberDatePickerState].
 * @param modifier the [Modifier] to be applied to this date picker
 * @param dateFormatter a [DatePickerFormatter] that provides formatting patterns for dates display
 * @param dateValidator a lambda that takes a date timestamp and return true if the date is a valid
 * one for selection. Invalid dates will appear disabled in the UI.
 * @param title the title to be displayed in the date picker
 * @param headline the headline to be displayed in the date picker
 * @param colors [DatePickerColors] that will be used to resolve the colors used for this date
 * picker in different states. See [DatePickerDefaults.colors].
 */
@ExperimentalMaterial3Api
@Composable
fun DatePicker(
    datePickerState: DatePickerState,
    modifier: Modifier = Modifier,
    dateFormatter: DatePickerFormatter = remember { DatePickerFormatter() },
    dateValidator: (Long) -> Boolean = { true },
    title: (@Composable () -> Unit)? = { DatePickerDefaults.DatePickerTitle() },
    headline: @Composable () -> Unit = {
        DatePickerDefaults.DatePickerHeadline(
            datePickerState,
            dateFormatter
        )
    },
    colors: DatePickerColors = DatePickerDefaults.colors()
) = DatePickerImpl(
    modifier = modifier,
    datePickerState = datePickerState,
    dateFormatter = dateFormatter,
    dateValidator = dateValidator,
    title = title,
    headline = headline,
    colors = colors
)

/**
 * Contains default values used by the date pickers.
 */
@ExperimentalMaterial3Api
object DatePickerDefaults {

    /**
     * Creates a [DatePickerColors] that will potentially animate between the provided colors
     * according to the Material specification.
     *
     * @param containerColor the color used for the date picker's background
     * @param titleContentColor the color used for the date picker's title
     * @param headlineContentColor the color used for the date picker's headline
     * @param weekdayContentColor the color used for the weekday letters
     * @param subheadContentColor the color used for the month and year subhead labels that appear
     * when the date picker is scrolling calendar months vertically
     * @param yearContentColor the color used for the year item when selecting a year
     * @param currentYearContentColor the color used for the current year content when selecting a
     * year
     * @param selectedYearContentColor the color used for the selected year content when selecting a
     * year
     * @param selectedYearContainerColor the color used for the selected year container when
     * selecting a year
     * @param dayContentColor the color used for days content
     * @param disabledDayContentColor the color used for disabled days content
     * @param selectedDayContentColor the color used for selected days content
     * @param disabledSelectedDayContentColor the color used for disabled selected days content
     * @param selectedDayContainerColor the color used for a selected day container
     * @param disabledSelectedDayContainerColor the color used for a disabled selected day container
     * @param todayContentColor the color used for the day that marks the current date
     * @param todayDateBorderColor the color used for the border of the day that marks the current
     * date
     */
    @Composable
    fun colors(
        containerColor: Color = DatePickerModalTokens.ContainerColor.toColor(),
        titleContentColor: Color = DatePickerModalTokens.HeaderSupportingTextColor.toColor(),
        headlineContentColor: Color = DatePickerModalTokens.HeaderHeadlineColor.toColor(),
        weekdayContentColor: Color = DatePickerModalTokens.WeekdaysLabelTextColor.toColor(),
        subheadContentColor: Color =
            DatePickerModalTokens.RangeSelectionMonthSubheadColor.toColor(),
        yearContentColor: Color =
            DatePickerModalTokens.SelectionYearUnselectedLabelTextColor.toColor(),
        currentYearContentColor: Color = DatePickerModalTokens.DateTodayLabelTextColor.toColor(),
        selectedYearContentColor: Color =
            DatePickerModalTokens.SelectionYearSelectedLabelTextColor.toColor(),
        selectedYearContainerColor: Color =
            DatePickerModalTokens.SelectionYearSelectedContainerColor.toColor(),
        dayContentColor: Color = DatePickerModalTokens.DateUnselectedLabelTextColor.toColor(),
        // TODO: Missing token values for the disabled colors.
        disabledDayContentColor: Color = dayContentColor.copy(alpha = 0.38f),
        selectedDayContentColor: Color = DatePickerModalTokens.DateSelectedLabelTextColor.toColor(),
        // TODO: Missing token values for the disabled colors.
        disabledSelectedDayContentColor: Color = selectedDayContentColor.copy(alpha = 0.38f),
        selectedDayContainerColor: Color =
            DatePickerModalTokens.DateSelectedContainerColor.toColor(),
        // TODO: Missing token values for the disabled colors.
        disabledSelectedDayContainerColor: Color = selectedDayContainerColor.copy(alpha = 0.38f),
        todayContentColor: Color = DatePickerModalTokens.DateTodayLabelTextColor.toColor(),
        todayDateBorderColor: Color =
            DatePickerModalTokens.DateTodayContainerOutlineColor.toColor(),
    ): DatePickerColors =
        DatePickerColors(
            containerColor = containerColor,
            titleContentColor = titleContentColor,
            headlineContentColor = headlineContentColor,
            weekdayContentColor = weekdayContentColor,
            subheadContentColor = subheadContentColor,
            yearContentColor = yearContentColor,
            currentYearContentColor = currentYearContentColor,
            selectedYearContentColor = selectedYearContentColor,
            selectedYearContainerColor = selectedYearContainerColor,
            dayContentColor = dayContentColor,
            disabledDayContentColor = disabledDayContentColor,
            selectedDayContentColor = selectedDayContentColor,
            disabledSelectedDayContentColor = disabledSelectedDayContentColor,
            selectedDayContainerColor = selectedDayContainerColor,
            disabledSelectedDayContainerColor = disabledSelectedDayContainerColor,
            todayContentColor = todayContentColor,
            todayDateBorderColor = todayDateBorderColor
        )

    /** A default date picker title composable. */
    @Composable
    fun DatePickerTitle() = Text(getString(string = Strings.DatePickerTitle))

    /**
     * A default date picker headline composable lambda that displays a default headline text when
     * there is no date selection, and an actual date string when there is.
     */
    @Composable
    fun DatePickerHeadline(state: DatePickerState, dateFormatter: DatePickerFormatter) {
        val formattedDate = dateFormatter.formatDate(
            date = state.selectedDate,
            calendarModel = state.calendarModel
        )
        if (formattedDate == null) {
            Text(getString(string = Strings.DatePickerHeadline), maxLines = 1)
        } else {
            Text(formattedDate, maxLines = 1)
        }
    }

    /** The range of years for the date picker dialogs. */
    val YearsRange: IntRange = IntRange(1900, 2100)

    /** The default shape for date picker dialogs. */
    val shape: Shape @Composable get() = DatePickerModalTokens.ContainerShape.toShape()
}

/**
 * Creates a [DatePickerState] for a date picker that is remembered across compositions.
 *
 * @param initialSelectedDateMillis timestamp in _UTC_ milliseconds from the epoch that represents
 * an initial selection of a date. Provide a `null` to indicate no selection.
 * @param initialDisplayedMonthMillis timestamp in _UTC_ milliseconds from the epoch that represents
 * an initial selection of a month to be displayed to the user. By default, in case an
 * `initialSelectedDateMillis` is provided, the initial displayed month would be the month of the
 * selected date. Otherwise, in case `null` is provided, the displayed month would be the
 * current one.
 * @param yearsRange a pair that holds the years range that the date picker should be limited to
 */
@Composable
@ExperimentalMaterial3Api
fun rememberDatePickerState(
    @Suppress("AutoBoxing") initialSelectedDateMillis: Long? = null,
    @Suppress("AutoBoxing") initialDisplayedMonthMillis: Long? = initialSelectedDateMillis,
    yearsRange: IntRange = DatePickerDefaults.YearsRange
): DatePickerState = rememberSaveable(
    yearsRange,
    saver = DatePickerState.Saver(
        yearsRange = yearsRange,
    )
) {
    DatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis,
        initialDisplayedMonthMillis = initialDisplayedMonthMillis,
        yearsRange = yearsRange
    )
}

/**
 * A state object that can be hoisted to observe the date picker state.
 *
 * @param initialSelectedDateMillis timestamp in _UTC_ milliseconds from the epoch that represents
 * an initial selection of a date. Provide a `null` to indicate no selection.
 * @param initialDisplayedMonthMillis timestamp in _UTC_ milliseconds from the epoch that represents
 * an initial selection of a month to be displayed to the user. In case `null` is provided, the
 * displayed month would be the current one.
 * @param yearsRange a pair that holds the years range that the date picker should be limited to
 * @see rememberDatePickerState
 */
@ExperimentalMaterial3Api
@Stable
class DatePickerState constructor(
    @Suppress("AutoBoxing") initialSelectedDateMillis: Long?,
    @Suppress("AutoBoxing") initialDisplayedMonthMillis: Long?,
    val yearsRange: IntRange
) {
    internal var calendarModel: CalendarModel = createCalendarModel()

    /**
     * A timestamp of the selected date in _UTC_ milliseconds from the epoch.
     *
     * In case no date was selected or provided, the state will hold a `null` value.
     */
    @get:Suppress("AutoBoxing")
    val selectedDateMillis by derivedStateOf {
        selectedDate?.utcTimeMillis
    }

    /**
     * A mutable state of [CalendarDate] that represents the selected date.
     */
    internal var selectedDate by mutableStateOf(
        if (initialSelectedDateMillis != null) {
            calendarModel.getDate(
                initialSelectedDateMillis
            )
        } else {
            null
        }
    )

    /**
     * A mutable state for the month that is displayed to the user. In case an initial month was not
     * provided, the current month will be the one to be displayed.
     */
    internal var displayedMonth by mutableStateOf(
        if (initialDisplayedMonthMillis != null) {
            calendarModel.getMonth(initialDisplayedMonthMillis)
        } else {
            currentMonth
        }
    )

    /**
     * The current [CalendarMonth] that represents the present's day month.
     */
    internal val currentMonth: CalendarMonth
        get() = calendarModel.getMonth(calendarModel.today)

    /** Sets the next month to be the [displayedMonth]. */
    internal fun displayNextMonth() {
        displayedMonth = calendarModel.plusMonths(displayedMonth, 1)
    }

    /** Sets the previous month to be the [displayedMonth]. */
    internal fun displayPreviousMonth() {
        displayedMonth = calendarModel.minusMonths(displayedMonth, 1)
    }

    /**
     * The displayed month index within the total months at the defined years range.
     *
     * @see [displayedMonth]
     * @see [yearsRange]
     */
    internal val displayedMonthIndex: Int
        get() = displayedMonth.indexIn(yearsRange)

    /**
     * The total month count for the defined years range.
     *
     * @see [yearsRange]
     */
    internal val totalMonthsInRange: Int
        get() = (yearsRange.last - yearsRange.first + 1) * 12

    companion object {
        /**
         * The default [Saver] implementation for [DatePickerState].
         */
        fun Saver(
            yearsRange: IntRange
        ): Saver<DatePickerState, *> = Saver(
            save = {
                listOf(
                    it.selectedDateMillis,
                    it.displayedMonth.startUtcTimeMillis
                )
            },
            restore = { value ->
                DatePickerState(
                    initialSelectedDateMillis = value[0],
                    initialDisplayedMonthMillis = value[1],
                    yearsRange = yearsRange
                )
            }
        )
    }
}

/**
 * Represents the colors used by the date picker.
 *
 * See [DatePickerDefaults.colors] for the default implementation that follows Material
 * specifications.
 */
@ExperimentalMaterial3Api
@Immutable
class DatePickerColors internal constructor(
    internal val containerColor: Color,
    internal val titleContentColor: Color,
    internal val headlineContentColor: Color,
    internal val weekdayContentColor: Color,
    internal val subheadContentColor: Color,
    private val yearContentColor: Color,
    private val currentYearContentColor: Color,
    private val selectedYearContentColor: Color,
    private val selectedYearContainerColor: Color,
    private val dayContentColor: Color,
    private val disabledDayContentColor: Color,
    private val selectedDayContentColor: Color,
    private val disabledSelectedDayContentColor: Color,
    private val selectedDayContainerColor: Color,
    private val disabledSelectedDayContainerColor: Color,
    private val todayContentColor: Color,
    internal val todayDateBorderColor: Color
) {
    /**
     * Represents the content color for a calendar day.
     *
     * @param today indicates that the color is for a date that represents today
     * @param selected indicates that the color is for a selected day
     * @param enabled indicates that the day is enabled for selection
     */
    @Composable
    internal fun dayContentColor(
        today: Boolean,
        selected: Boolean,
        enabled: Boolean
    ): State<Color> {
        val target = if (selected) {
            if (enabled) selectedDayContentColor else disabledSelectedDayContentColor
        } else if (today) {
            todayContentColor
        } else {
            if (enabled) dayContentColor else disabledDayContentColor
        }

        return animateColorAsState(
            target,
            tween(durationMillis = MotionTokens.DurationShort2.toInt())
        )
    }

    /**
     * Represents the container color for a calendar day.
     *
     * @param selected indicates that the color is for a selected day
     * @param enabled indicates that the day is enabled for selection
     * @param animate whether or not to animate a container color change
     */
    @Composable
    internal fun dayContainerColor(
        selected: Boolean,
        enabled: Boolean,
        animate: Boolean
    ): State<Color> {
        val target = if (selected) {
            if (enabled) selectedDayContainerColor else disabledSelectedDayContainerColor
        } else {
            Color.Transparent
        }
        return if (animate) {
            animateColorAsState(
                target,
                tween(durationMillis = MotionTokens.DurationShort2.toInt())
            )
        } else {
            rememberUpdatedState(target)
        }
    }

    /**
     * Represents the content color for a calendar year.
     *
     * @param currentYear indicates that the color is for a year that represents the current year
     * @param selected indicates that the color is for a selected year
     */
    @Composable
    internal fun yearContentColor(currentYear: Boolean, selected: Boolean): State<Color> {
        val target = if (selected) {
            selectedYearContentColor
        } else if (currentYear) {
            currentYearContentColor
        } else {
            yearContentColor
        }

        return animateColorAsState(
            target,
            tween(durationMillis = MotionTokens.DurationShort2.toInt())
        )
    }

    /**
     * Represents the container color for a calendar year.
     *
     * @param selected indicates that the color is for a selected day
     */
    @Composable
    internal fun yearContainerColor(selected: Boolean): State<Color> {
        val target = if (selected) selectedYearContainerColor else Color.Transparent
        return animateColorAsState(
            target,
            tween(durationMillis = MotionTokens.DurationShort2.toInt())
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DatePickerColors) return false
        if (containerColor != other.containerColor) return false
        if (titleContentColor != other.titleContentColor) return false
        if (headlineContentColor != other.headlineContentColor) return false
        if (weekdayContentColor != other.weekdayContentColor) return false
        if (subheadContentColor != other.subheadContentColor) return false
        if (yearContentColor != other.yearContentColor) return false
        if (currentYearContentColor != other.currentYearContentColor) return false
        if (selectedYearContentColor != other.selectedYearContentColor) return false
        if (selectedYearContainerColor != other.selectedYearContainerColor) return false
        if (dayContentColor != other.dayContentColor) return false
        if (disabledDayContentColor != other.disabledDayContentColor) return false
        if (selectedDayContentColor != other.selectedDayContentColor) return false
        if (disabledSelectedDayContentColor != other.disabledSelectedDayContentColor) return false
        if (selectedDayContainerColor != other.selectedDayContainerColor) return false
        if (disabledSelectedDayContainerColor != other.disabledSelectedDayContainerColor) {
            return false
        }
        if (todayContentColor != other.todayContentColor) return false
        if (todayDateBorderColor != other.todayDateBorderColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + titleContentColor.hashCode()
        result = 31 * result + headlineContentColor.hashCode()
        result = 31 * result + weekdayContentColor.hashCode()
        result = 31 * result + subheadContentColor.hashCode()
        result = 31 * result + yearContentColor.hashCode()
        result = 31 * result + currentYearContentColor.hashCode()
        result = 31 * result + selectedYearContentColor.hashCode()
        result = 31 * result + selectedYearContainerColor.hashCode()
        result = 31 * result + dayContentColor.hashCode()
        result = 31 * result + disabledDayContentColor.hashCode()
        result = 31 * result + selectedDayContentColor.hashCode()
        result = 31 * result + disabledSelectedDayContentColor.hashCode()
        result = 31 * result + selectedDayContainerColor.hashCode()
        result = 31 * result + disabledSelectedDayContainerColor.hashCode()
        result = 31 * result + todayContentColor.hashCode()
        result = 31 * result + todayDateBorderColor.hashCode()
        return result
    }
}

/**
 * A date formatter used by [DatePicker].
 *
 * @param shortFormat date format for displaying a date in a short length string, and is used
 * when a selected date is at the current year (e.g. Mon, Aug 17)
 * @param mediumFormat date format for displaying a date in a medium length string, and is used
 * when a selected date is at a different year (e.g. Sep 17, 1999)
 * @param monthYearFormat date format for displaying a date as a month and a year (e.g.
 * September 2022)
 */
@ExperimentalMaterial3Api
@Immutable
class DatePickerFormatter constructor(
    internal val shortFormat: String = "E, MMM d", // e.g. Mon, Aug 17
    internal val mediumFormat: String = "MMM d, yyyy", // e.g. Aug 17, 2022,
    internal val monthYearFormat: String = "MMMM yyyy", // e.g. September 2022
) {

    internal fun formatMonthYear(month: CalendarMonth?, calendarModel: CalendarModel): String? {
        if (month == null) return null
        return calendarModel.format(month, monthYearFormat)
    }

    internal fun formatDate(date: CalendarDate?, calendarModel: CalendarModel): String? {
        if (date == null) return null
        val pattern = if (calendarModel.today.year == date.year) {
            shortFormat
        } else {
            mediumFormat
        }
        return calendarModel.format(date, pattern)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DatePickerFormatter) return false

        if (monthYearFormat != other.monthYearFormat) return false
        if (shortFormat != other.shortFormat) return false
        if (mediumFormat != other.mediumFormat) return false

        return true
    }

    override fun hashCode(): Int {
        var result = monthYearFormat.hashCode()
        result = 31 * result + shortFormat.hashCode()
        result = 31 * result + mediumFormat.hashCode()
        return result
    }
}

@ExperimentalMaterial3Api
@Composable
private fun DatePickerImpl(
    modifier: Modifier,
    datePickerState: DatePickerState,
    dateFormatter: DatePickerFormatter,
    dateValidator: (Long) -> Boolean,
    title: (@Composable () -> Unit)?,
    headline: @Composable () -> Unit,
    colors: DatePickerColors
) {
    Column(
        modifier = modifier
            .sizeIn(minWidth = ContainerWidth, minHeight = ContainerHeight)
            .padding(DatePickerHorizontalPadding)
    ) {
        DatePickerHeader(
            modifier = Modifier,
            title = title,
            titleContentColor = colors.titleContentColor,
            headlineContentColor = colors.headlineContentColor
        ) {
            headline()
        }

        val monthsLazyListState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = datePickerState.displayedMonthIndex
            )

        val onDateSelected = { dateInMillis: Long ->
            datePickerState.selectedDate = datePickerState.calendarModel.getDate(dateInMillis)
        }
        Divider()
        HorizontalDatePicker(
            onDateSelected = onDateSelected,
            datePickerState = datePickerState,
            monthsLazyListState = monthsLazyListState,
            dateFormatter = dateFormatter,
            dateValidator = dateValidator,
            colors = colors
        )
    }
}

@Composable
private fun DatePickerHeader(
    modifier: Modifier,
    title: (@Composable () -> Unit)?,
    titleContentColor: Color,
    headlineContentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .requiredHeight(DatePickerModalTokens.HeaderContainerHeight)
            .padding(HeaderPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                // TODO: Use the value from the tokens, once updated (b/251240936).
                val textStyle = MaterialTheme.typography.fromToken(HeaderSupportingTextFont)
                ProvideTextStyle(textStyle) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        title()
                    }
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides headlineContentColor) {
            val textStyle =
                MaterialTheme.typography.fromToken(DatePickerModalTokens.HeaderHeadlineFont)
            ProvideTextStyle(textStyle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalDatePicker(
    onDateSelected: (dateInMillis: Long) -> Unit,
    datePickerState: DatePickerState,
    monthsLazyListState: LazyListState,
    dateFormatter: DatePickerFormatter,
    dateValidator: (Long) -> Boolean,
    colors: DatePickerColors
) {
    val coroutineScope = rememberCoroutineScope()

    val nextMonthAvailable by remember(monthsLazyListState, datePickerState) {
        derivedStateOf {
            monthsLazyListState.firstVisibleItemIndex < datePickerState.totalMonthsInRange - 1
        }
    }
    val previousMonthAvailable by remember(monthsLazyListState, datePickerState) {
        derivedStateOf {
            monthsLazyListState.firstVisibleItemIndex > 0
        }
    }

    var yearPickerVisible by rememberSaveable { mutableStateOf(false) }
    MonthsNavigation(
        nextAvailable = nextMonthAvailable,
        previousAvailable = previousMonthAvailable,
        yearPickerVisible = yearPickerVisible,
        yearPickerText = dateFormatter.formatMonthYear(
            datePickerState.displayedMonth,
            datePickerState.calendarModel
        ) ?: "-",
        onNextClicked = {
            datePickerState.displayNextMonth()
            coroutineScope.launch {
                monthsLazyListState.animateScrollToItem(
                    monthsLazyListState.firstVisibleItemIndex + 1
                )
            }
        },
        onPreviousClicked = {
            datePickerState.displayPreviousMonth()
            coroutineScope.launch {
                monthsLazyListState.animateScrollToItem(
                    monthsLazyListState.firstVisibleItemIndex - 1
                )
            }
        },
        onYearPickerButtonClicked = { yearPickerVisible = !yearPickerVisible }
    )
    // TODO Add motion for switching between the modes.
    if (yearPickerVisible) {
        YearPicker(
            onYearSelected = { year ->
                // Switch back to the monthly calendar and scroll to the selected year.
                yearPickerVisible = !yearPickerVisible
                coroutineScope.launch {
                    // Scroll to the selected year (maintaining the month of year).
                    // A LaunchEffect at the MonthsPager will take care of rest and will update
                    // the state's displayedMonth to the month we scrolled to.
                    monthsLazyListState.scrollToItem(
                        (year - datePickerState.yearsRange.first) * 12 +
                            datePickerState.displayedMonth.month - 1
                    )
                }
            },
            colors = colors,
            datePickerState = datePickerState
        )
        Divider()
    } else {
        WeekDays(colors, datePickerState.calendarModel)
        HorizontalMonthsList(
            onMonthChanged = { year, month ->
                datePickerState.displayedMonth =
                    datePickerState.calendarModel.getMonth(year, month)
            },
            onDateSelected = onDateSelected,
            datePickerState = datePickerState,
            lazyListState = monthsLazyListState,
            dateValidator = dateValidator,
            colors = colors
        )
    }
}

/**
 * Composes a horizontal pageable list of months.
 */
@Suppress("IllegalExperimentalApiUsage") // TODO (b/261627439): Address before moving to beta
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HorizontalMonthsList(
    onMonthChanged: (year: Int, month: Int) -> Unit,
    onDateSelected: (dateInMillis: Long) -> Unit,
    datePickerState: DatePickerState,
    lazyListState: LazyListState,
    dateValidator: (Long) -> Boolean,
    colors: DatePickerColors,
) {
    val today = datePickerState.calendarModel.today
    MonthsListWrapper(
        lazyListState = lazyListState,
        datePickerState = datePickerState,
        onMonthChanged = onMonthChanged
    ) { firstMonth ->
        LazyRow(
            state = lazyListState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState)
        ) {
            items(datePickerState.totalMonthsInRange) {
                val month =
                    datePickerState.calendarModel.plusMonths(
                        from = firstMonth,
                        addedMonthsCount = it
                    )
                Box(
                    modifier = Modifier.fillParentMaxWidth()
                ) {
                    Month(
                        month = month,
                        onDateSelected = onDateSelected,
                        today = today,
                        selectedDate = datePickerState.selectedDate,
                        dateValidator = dateValidator,
                        colors = colors
                    )
                }
            }
        }
    }
}

/**
 * A wrapper for composing a list of months.
 *
 * The direction of the list is determined by the [content]. See [HorizontalMonthsList].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthsListWrapper(
    lazyListState: LazyListState,
    datePickerState: DatePickerState,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    content: @Composable (firstMonth: CalendarMonth) -> Unit
) {
    val firstMonth = remember(datePickerState.yearsRange) {
        datePickerState.calendarModel.getMonth(
            year = datePickerState.yearsRange.first,
            month = 1 // January
        )
    }

    content(firstMonth)

    // Ensures that the onMonthChanged is only called when the month is changing by scrolling this
    // pager and not by the arrow actions above it.
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }.collect {
            val yearOffset = lazyListState.firstVisibleItemIndex / 12
            val month = lazyListState.firstVisibleItemIndex % 12 + 1
            with(datePickerState) {
                if (displayedMonth.month != month ||
                    displayedMonth.year != yearsRange.first + yearOffset
                ) {
                    onMonthChanged(yearsRange.first + yearOffset, month)
                }
            }
        }
    }
}

/**
 * Composes the weekdays letters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekDays(colors: DatePickerColors, calendarModel: CalendarModel) {
    val firstDayOfWeek = calendarModel.firstDayOfWeek
    val weekdays = calendarModel.weekdayNames
    val dayNames = arrayListOf<Pair<String, String>>()
    // Start with firstDayOfWeek - 1 as the days are 1-based.
    for (i in firstDayOfWeek - 1 until weekdays.size) {
        dayNames.add(weekdays[i])
    }
    for (i in 0 until firstDayOfWeek - 1) {
        dayNames.add(weekdays[i])
    }
    CompositionLocalProvider(LocalContentColor provides colors.weekdayContentColor) {
        // TODO: Use the value from the tokens, once updated (b/251240936).
        val textStyle = MaterialTheme.typography.fromToken(WeekdaysLabelTextFont)
        ProvideTextStyle(value = textStyle) {
            // Although the days can be composed in a Row, here we use a LazyVerticalGrid to match
            // the exact spacing that will appear at the calendar grid underneath on every
            // orientation.
            LazyVerticalGrid(
                columns = GridCells.Fixed(DaysInWeek),
                modifier = Modifier.defaultMinSize(
                    minHeight = RecommendedSizeForAccessibility
                ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                userScrollEnabled = false
            ) {
                items(items = dayNames) {
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics { contentDescription = it.first }
                            .size(
                                width = RecommendedSizeForAccessibility,
                                height = RecommendedSizeForAccessibility
                            ),
                        contentAlignment = Alignment.Center) {
                        Text(
                            text = it.second,
                            modifier = Modifier.wrapContentSize(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable that renders a calendar month and displays a date selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Month(
    month: CalendarMonth,
    onDateSelected: (dateInMillis: Long) -> Unit,
    today: CalendarDate,
    selectedDate: CalendarDate?,
    dateValidator: (Long) -> Boolean,
    colors: DatePickerColors
) {
    ProvideTextStyle(
        // TODO: Use the value from the tokens, once updated (b/251240936).
        MaterialTheme.typography.fromToken(DateLabelTextFont)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(DaysInWeek),
            modifier = Modifier
                .requiredHeight(RecommendedSizeForAccessibility * MaxCalendarRows),
            horizontalArrangement = Arrangement.SpaceEvenly,
            userScrollEnabled = false
        ) {
            // Fill the empty slots until the first day of the month.
            items(month.daysFromStartOfWeekToFirstOfMonth) {
                Box(
                    modifier = Modifier.requiredSize(
                        width = RecommendedSizeForAccessibility,
                        height = RecommendedSizeForAccessibility
                    )
                )
            }

            items(count = month.numberOfDays) {
                // TODO a11y should announce the day and whether it's selected or not.
                val dateInMillis = month.startUtcTimeMillis + (it * MillisecondsIn24Hours)
                Day(
                    checked = dateInMillis == selectedDate?.utcTimeMillis,
                    onCheckedChange = { onDateSelected(dateInMillis) },
                    animateChecked = true,
                    enabled = remember(dateInMillis) {
                        dateValidator.invoke(dateInMillis)
                    },
                    today = dateInMillis == today.utcTimeMillis,
                    text = (it + 1).toLocalString(),
                    colors = colors
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Day(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    animateChecked: Boolean,
    enabled: Boolean,
    today: Boolean,
    text: String,
    colors: DatePickerColors
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .requiredSize(
                DatePickerModalTokens.DateStateLayerWidth,
                DatePickerModalTokens.DateStateLayerHeight
            )
            .clip(DatePickerModalTokens.DateContainerShape.toShape())
            .background(
                colors.dayContainerColor(
                    selected = checked,
                    enabled = enabled,
                    animate = animateChecked
                ).value
            )
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(
                    bounded = true,
                    radius = DatePickerModalTokens.DateStateLayerWidth / 2
                )
            )
            .then(
                // Add a border to mark the current day (i.e. today).
                if (today && !checked) {
                    Modifier.border(
                        BorderStroke(
                            DatePickerModalTokens.DateTodayContainerOutlineWidth,
                            colors.todayDateBorderColor
                        ), DatePickerModalTokens.DateContainerShape.toShape()
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = colors.dayContentColor(
            today = today,
            selected = checked,
            enabled = enabled,
        ).value
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = text,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearPicker(
    onYearSelected: (year: Int) -> Unit,
    colors: DatePickerColors,
    datePickerState: DatePickerState
) {
    ProvideTextStyle(
        value = MaterialTheme.typography.fromToken(DatePickerModalTokens.SelectionYearLabelTextFont)
    ) {
        val currentYear = datePickerState.currentMonth.year
        val displayedYear = datePickerState.displayedMonth.year
        val lazyGridState =
            rememberLazyGridState(
                // Set the initial index to a few years before the current year to allow quicker
                // selection of previous years.
                initialFirstVisibleItemIndex = max(
                    0, displayedYear - datePickerState.yearsRange.first - YearsInRow
                )
            )
        LazyVerticalGrid(
            columns = GridCells.Fixed(YearsInRow),
            // Keep the height the same as the monthly calendar height + weekdays height.
            modifier = Modifier.requiredHeight(
                RecommendedSizeForAccessibility * (MaxCalendarRows + 1)
            ),
            state = lazyGridState,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(YearsVerticalPadding)
        ) {
            items(datePickerState.yearsRange.count()) {
                val selectedYear = it + datePickerState.yearsRange.first
                Year(
                    checked = selectedYear == displayedYear,
                    currentYear = selectedYear == currentYear,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onYearSelected(selectedYear)
                        }
                    },
                    modifier = Modifier.requiredSize(
                        width = DatePickerModalTokens.SelectionYearContainerWidth,
                        height = DatePickerModalTokens.SelectionYearContainerHeight
                    ),
                    text = selectedYear.toLocalString(),
                    colors = colors
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Year(
    checked: Boolean,
    currentYear: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    text: String,
    colors: DatePickerColors
) {
    val border = remember(currentYear, checked) {
        if (currentYear && !checked) {
            // Use the day's spec to draw a border around the current year.
            BorderStroke(
                DatePickerModalTokens.DateTodayContainerOutlineWidth,
                colors.todayDateBorderColor
            )
        } else {
            null
        }
    }
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        shape = DatePickerModalTokens.SelectionYearStateLayerShape.toShape(),
        color = colors.yearContainerColor(selected = checked).value,
        contentColor = colors.yearContentColor(
            currentYear = currentYear,
            selected = checked
        ).value,
        border = border
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A composable that shows a year menu button and a couple of buttons that enable navigation between
 * displayed months.
 */
@Composable
private fun MonthsNavigation(
    nextAvailable: Boolean,
    previousAvailable: Boolean,
    yearPickerVisible: Boolean,
    yearPickerText: String,
    onNextClicked: () -> Unit,
    onPreviousClicked: () -> Unit,
    onYearPickerButtonClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(MonthYearHeight),
        horizontalArrangement = if (yearPickerVisible) {
            Arrangement.Start
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A menu button for selecting a year.
        YearPickerMenuButton(
            onClick = onYearPickerButtonClicked,
            text = yearPickerText,
            expanded = yearPickerVisible
        )
        // Show arrows for traversing months (only visible when the year selection is off)
        if (!yearPickerVisible) {
            Row {
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                IconButton(onClick = onPreviousClicked, enabled = previousAvailable) {
                    Icon(
                        if (rtl) {
                            Icons.Filled.KeyboardArrowRight
                        } else {
                            Icons.Filled.KeyboardArrowLeft
                        },
                        contentDescription = getString(Strings.DatePickerPreviousMonth)
                    )
                }
                IconButton(onClick = onNextClicked, enabled = nextAvailable) {
                    Icon(
                        if (rtl) {
                            Icons.Filled.KeyboardArrowLeft
                        } else {
                            Icons.Filled.KeyboardArrowRight
                        },
                        contentDescription = getString(Strings.DatePickerNextMonth)
                    )
                }
            }
        }
    }
}

// TODO: Replace with the official MenuButton when implemented.
@Composable
private fun YearPickerMenuButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RectangleShape,
        colors =
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        elevation = null,
        border = null,
        interactionSource = interactionSource,
    ) {
        Text(text = text)
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = if (expanded) {
                getString(Strings.DatePickerSwitchToDaySelection)
            } else {
                getString(Strings.DatePickerSwitchToYearSelection)
            },
            Modifier.rotate(if (expanded) 180f else 0f)
        )
    }
}

/**
 * Returns a string representation of an integer at the current Locale.
 */
private fun Int.toLocalString(): String {
    val formatter = NumberFormat.getIntegerInstance()
    // Eliminate any use of delimiters when formatting the integer.
    formatter.isGroupingUsed = false
    return formatter.format(this)
}

internal val MonthYearHeight = 56.dp
internal val DatePickerHorizontalPadding = PaddingValues(horizontal = 12.dp)
internal val HeaderPadding = PaddingValues(
    start = 12.dp,
    top = 16.dp,
    bottom = 12.dp
)
internal val CalendarMonthSubheadPadding = PaddingValues(
    start = 12.dp,
    top = 20.dp,
    bottom = 8.dp
)
private val YearsVerticalPadding = 16.dp

private const val MaxCalendarRows = 6
private const val YearsInRow: Int = 3

// TODO: Remove after b/247694457 for updating the tokens is resolved.
private val ContainerWidth = 360.dp
private val ContainerHeight = 568.dp

// TODO: Remove after b/251240936 for updating the typography is resolved.
private val WeekdaysLabelTextFont = TypographyKeyTokens.BodyLarge
private val DateLabelTextFont = TypographyKeyTokens.BodyLarge
private val HeaderSupportingTextFont = TypographyKeyTokens.LabelLarge

private val RecommendedSizeForAccessibility = 48.dp
