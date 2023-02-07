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

package androidx.wear.compose.integration.demos

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PickerGroupItem
import androidx.wear.compose.material.PickerDefaults
import androidx.wear.compose.material.PickerGroup
import androidx.wear.compose.material.PickerGroupState
import androidx.wear.compose.material.PickerScope
import androidx.wear.compose.material.PickerState
import androidx.wear.compose.material.TouchExplorationStateProvider
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerGroupState
import androidx.wear.compose.material.rememberPickerState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAdjusters

/**
 * A full screen TimePicker with hours, minutes and seconds.
 *
 * This component is designed to take most/all of the screen and utilizes large fonts. In order to
 * ensure that it will draw properly on smaller screens it does not take account of user font size
 * overrides for MaterialTheme.typography.display3 which is used to display the main picker
 * value.
 *
 * @param onTimeConfirm the button event handler.
 * @param modifier the modifiers for the `Box` containing the UI elements.
 * @param time the initial value to seed the picker with.
 */
@SuppressLint("ClassVerificationFailure")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
public fun TimePicker(
    onTimeConfirm: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    time: LocalTime = LocalTime.now()
) {
    // Omit scaling according to Settings > Display > Font size for this screen
    val typography = MaterialTheme.typography.copy(
        display3 = MaterialTheme.typography.display3.copy(
            fontSize = with(LocalDensity.current) { 30.dp.toSp() }
        )
    )
    val hourState = rememberPickerState(
        initialNumberOfOptions = 24,
        initiallySelectedOption = time.hour
    )
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = time.minute
    )
    val secondState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = time.second
    )
    val touchExplorationServicesEnabled by DefaultTouchExplorationStateProvider()
        .touchExplorationState()

    MaterialTheme(typography = typography) {
        // When the time picker loads, none of the individual pickers are selected in talkback mode,
        // otherwise hours picker should be focused.
        val pickerGroupState = if (touchExplorationServicesEnabled) {
            rememberPickerGroupState(-1)
        } else {
            rememberPickerGroupState(0)
        }
        val textStyle = MaterialTheme.typography.display3
        val optionColor = MaterialTheme.colors.secondary
        val pickerOption = pickerTextOption(textStyle) { "%02d".format(it) }
        val focusRequesterConfirmButton = remember { FocusRequester() }

        val hourContentDescription by remember {
            derivedStateOf {
                createDescription(pickerGroupState, hourState.selectedOption, "hours")
            }
        }
        val minuteContentDescription by remember {
            derivedStateOf {
                createDescription(pickerGroupState, minuteState.selectedOption, "minutes")
            }
        }
        val secondContentDescription by remember {
            derivedStateOf {
                createDescription(pickerGroupState, secondState.selectedOption, "seconds")
            }
        }
        val onPickerSelected = { selectedPicker: Int ->
            if (pickerGroupState.selectedIndex != selectedPicker) {
                pickerGroupState.selectedIndex = selectedPicker
            } else if (selectedPicker > 2) {
                focusRequesterConfirmButton.requestFocus()
            } else {
                pickerGroupState.selectedIndex += 1
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when (pickerGroupState.selectedIndex) {
                        0 -> "Hour"
                        1 -> "Minute"
                        2 -> "Second"
                        else -> ""
                    },
                    color = optionColor,
                    style = MaterialTheme.typography.button,
                    maxLines = 1,
                )
                val weightsToCenterVertically = 0.5f
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(weightsToCenterVertically)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PickerGroup(
                        PickerGroupItem(
                            pickerState = hourState,
                            modifier = Modifier.size(40.dp, 100.dp),
                            focusRequester = remember { FocusRequester() },
                            onSelected = { onPickerSelected(0) },
                            contentDescription = hourContentDescription,
                            option = pickerOption
                        ),
                        PickerGroupItem(
                            pickerState = minuteState,
                            modifier = Modifier.size(40.dp, 100.dp),
                            focusRequester = remember { FocusRequester() },
                            onSelected = { onPickerSelected(1) },
                            contentDescription = minuteContentDescription,
                            option = pickerOption
                        ),
                        PickerGroupItem(
                            pickerState = secondState,
                            modifier = Modifier.size(40.dp, 100.dp),
                            focusRequester = remember { FocusRequester() },
                            onSelected = { onPickerSelected(2) },
                            contentDescription = secondContentDescription,
                            option = pickerOption
                        ),
                        pickerGroupState = pickerGroupState,
                        separator = { Separator(6.dp, textStyle) },
                        autoCenter = false
                    )
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(weightsToCenterVertically)
                )
                Button(
                    onClick = {
                        val confirmedTime = LocalTime.of(
                            hourState.selectedOption,
                            minuteState.selectedOption,
                            secondState.selectedOption
                        )
                        onTimeConfirm(confirmedTime)
                    },
                    modifier = Modifier
                        .semantics {
                            focused = pickerGroupState.selectedIndex > 2
                        }
                        .focusRequester(focusRequesterConfirmButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "confirm",
                        modifier = Modifier
                            .size(24.dp)
                            .wrapContentSize(align = Alignment.Center),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * A full screen TimePicker with hours and minutes and AM/PM selector.
 *
 * This component is designed to take most/all of the screen and utilizes large fonts. In order to
 * ensure that it will draw properly on smaller screens it does not take account of user font size
 * overrides for MaterialTheme.typography.display1 which is used to display the main picker
 * value.
 *
 * @param onTimeConfirm the button event handler.
 * @param modifier the modifiers for the `Column` containing the UI elements.
 * @param time the initial value to seed the picker with.
 */
@SuppressLint("ClassVerificationFailure")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
public fun TimePickerWith12HourClock(
    onTimeConfirm: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    time: LocalTime = LocalTime.now()
) {
    // Omit scaling according to Settings > Display > Font size for this screen,
    val typography = MaterialTheme.typography.copy(
        display1 = MaterialTheme.typography.display1.copy(
            fontSize = with(LocalDensity.current) { 40.dp.toSp() }
        )
    )
    val hourState = rememberPickerState(
        initialNumberOfOptions = 12,
        initiallySelectedOption = time[ChronoField.CLOCK_HOUR_OF_AMPM] - 1
    )
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = time.minute
    )
    val periodState = rememberPickerState(
        initialNumberOfOptions = 2,
        initiallySelectedOption = time[ChronoField.AMPM_OF_DAY],
        repeatItems = false
    )
    val touchExplorationServicesEnabled by DefaultTouchExplorationStateProvider()
        .touchExplorationState()

    MaterialTheme(typography = typography) {
        var focusedElement by remember {
            mutableStateOf(
                if (touchExplorationServicesEnabled) FocusableElement12Hour.NONE
                else FocusableElement12Hour.HOURS
            )
        }
        val textStyle = MaterialTheme.typography.display3
        val focusRequesterHours = remember { FocusRequester() }
        val focusRequesterMinutes = remember { FocusRequester() }
        val focusRequesterPeriod = remember { FocusRequester() }
        val focusRequesterConfirmButton = remember { FocusRequester() }

        val hoursContentDescription by remember { derivedStateOf {
                createDescription12Hour(focusedElement, hourState.selectedOption + 1, "hours")
            } }
        val minutesContentDescription by remember { derivedStateOf {
                createDescription12Hour(focusedElement, minuteState.selectedOption, "minutes")
            } }

        val amString = remember {
            LocalTime.of(6, 0).format(DateTimeFormatter.ofPattern("a"))
        }
        val pmString = remember {
            LocalTime.of(18, 0).format(DateTimeFormatter.ofPattern("a"))
        }
        val periodContentDescription by remember { derivedStateOf {
                if (focusedElement == FocusableElement12Hour.NONE)
                    createDescription12Hour(focusedElement, periodState.selectedOption, "period")
                else if (periodState.selectedOption == 0)
                    createDescription12Hour(focusedElement, periodState.selectedOption, amString)
                else createDescription12Hour(focusedElement, periodState.selectedOption, pmString)
            } }
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (touchExplorationServicesEnabled) {
                        when (focusedElement) {
                            FocusableElement12Hour.HOURS -> Modifier.scrollablePicker(hourState)
                            FocusableElement12Hour.MINUTES -> Modifier.scrollablePicker(minuteState)
                            FocusableElement12Hour.PERIOD -> Modifier.scrollablePicker(periodState)
                            else -> Modifier
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when (focusedElement) {
                        FocusableElement12Hour.HOURS -> "Hour"
                        FocusableElement12Hour.MINUTES -> "Minute"
                        else -> ""
                    },
                    color = MaterialTheme.colors.secondary,
                    style = MaterialTheme.typography.button,
                    maxLines = 1,
                )
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,

                    ) {
                    val doubleTapToNext =
                        { position: FocusableElement12Hour, next: FocusableElement12Hour ->
                            focusedElement = when (focusedElement) {
                                position -> next
                                else -> position
                            }
                        }
                    Spacer(Modifier.width(8.dp))
                    PickerWithRSB(
                        readOnly = focusedElement != FocusableElement12Hour.HOURS,
                        state = hourState,
                        focusRequester = focusRequesterHours,
                        modifier = Modifier.size(48.dp, 100.dp),
                        onSelected = {
                            doubleTapToNext(
                                FocusableElement12Hour.HOURS,
                                FocusableElement12Hour.MINUTES
                            )
                        },
                        contentDescription = hoursContentDescription,
                        userScrollEnabled = !touchExplorationServicesEnabled ||
                            focusedElement == FocusableElement12Hour.HOURS
                    ) { hour: Int ->
                        TimePiece(
                            selected = focusedElement == FocusableElement12Hour.HOURS,
                            onSelected = {
                                doubleTapToNext(
                                    FocusableElement12Hour.HOURS,
                                    FocusableElement12Hour.MINUTES
                                )
                            },
                            text = "%02d".format(hour + 1),
                            style = textStyle,
                            touchExplorationServicesEnabled = touchExplorationServicesEnabled
                        )
                    }
                    Separator(2.dp, textStyle)
                    PickerWithRSB(
                        readOnly = focusedElement != FocusableElement12Hour.MINUTES,
                        state = minuteState,
                        focusRequester = focusRequesterMinutes,
                        modifier = Modifier.size(48.dp, 100.dp),
                        onSelected = {
                            doubleTapToNext(
                                FocusableElement12Hour.MINUTES,
                                FocusableElement12Hour.PERIOD
                            )
                        },
                        contentDescription = minutesContentDescription,
                        userScrollEnabled = !touchExplorationServicesEnabled ||
                            focusedElement == FocusableElement12Hour.MINUTES
                    ) { minute: Int ->
                        TimePiece(
                            selected = focusedElement == FocusableElement12Hour.MINUTES,
                            onSelected = {
                                doubleTapToNext(
                                    FocusableElement12Hour.MINUTES,
                                    FocusableElement12Hour.PERIOD
                                )
                            },
                            text = "%02d".format(minute),
                            style = textStyle,
                            touchExplorationServicesEnabled = touchExplorationServicesEnabled
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    PickerWithRSB(
                        readOnly = focusedElement != FocusableElement12Hour.PERIOD,
                        state = periodState,
                        focusRequester = focusRequesterPeriod,
                        modifier = Modifier.size(64.dp, 100.dp),
                        onSelected = {
                            doubleTapToNext(
                                FocusableElement12Hour.PERIOD,
                                FocusableElement12Hour.CONFIRM_BUTTON
                            )
                        },
                        contentDescription = periodContentDescription,
                        userScrollEnabled = !touchExplorationServicesEnabled ||
                            focusedElement == FocusableElement12Hour.PERIOD
                    ) { period: Int ->
                        TimePiece(
                            selected = focusedElement == FocusableElement12Hour.PERIOD,
                            onSelected = {
                                doubleTapToNext(
                                    FocusableElement12Hour.PERIOD,
                                    FocusableElement12Hour.CONFIRM_BUTTON
                                )
                            },
                            text = if (period == 0) amString else pmString,
                            style = textStyle,
                            touchExplorationServicesEnabled = touchExplorationServicesEnabled
                        )
                    }
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                )
                Button(onClick = {
                    val confirmedTime = LocalTime.of(
                        hourState.selectedOption + 1,
                        minuteState.selectedOption,
                        0
                    ).with(ChronoField.AMPM_OF_DAY, periodState.selectedOption.toLong())
                    onTimeConfirm(confirmedTime)
                },
                    modifier = Modifier
                        .semantics {
                            focused = focusedElement == FocusableElement12Hour.CONFIRM_BUTTON
                        }
                        .focusRequester(focusRequesterConfirmButton)

                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "confirm",
                        modifier = Modifier
                            .size(24.dp)
                            .wrapContentSize(align = Alignment.Center),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(focusedElement) {
                    if (focusedElement != FocusableElement12Hour.NONE) {
                        listOf(
                            focusRequesterHours, focusRequesterMinutes, focusRequesterPeriod,
                            focusRequesterConfirmButton
                        )[focusedElement.index].requestFocus()
                    }
                }
            }
        }
    }
}

/**
 * Full screen date picker with day, month, year.
 *
 * This component is designed to take most/all of the screen and utilizes large fonts. In order to
 * ensure that it will draw properly on smaller screens it does not take account of user font size
 * overrides for MaterialTheme.typography.display2 which is used to display the main picker
 * value.
 *
 * @param onDateConfirm the button event handler.
 * @param modifier the modifiers for the `Box` containing the UI elements.
 * @param date the initial value to seed the picker with.
 * @param fromDate the minimum date to be selected in picker
 * @param toDate the maximum date to be selected in picker
 */
@SuppressLint("ClassVerificationFailure")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
public fun DatePicker(
    onDateConfirm: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate = LocalDate.now(),
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null
) {
    if (fromDate != null && toDate != null) {
        verifyDates(date, fromDate, toDate)
    }

    val datePickerState = remember(date) {
        if (fromDate != null && toDate == null) {
            DatePickerState(date = date, fromDate = fromDate, toDate = fromDate.plusYears(3000))
        } else if (fromDate == null && toDate != null) {
            DatePickerState(date = date, fromDate = toDate.minusYears(3000), toDate = toDate)
        } else {
            DatePickerState(date, fromDate, toDate)
        }
    }

    // Omit scaling according to Settings > Display > Font size for this screen
    val typography = MaterialTheme.typography.copy(
        display2 = MaterialTheme.typography.display2.copy(
            fontSize = with(LocalDensity.current) { 34.dp.toSp() }
        )
    )
    val touchExplorationServicesEnabled by DefaultTouchExplorationStateProvider()
        .touchExplorationState()

    MaterialTheme(typography = typography) {
        var focusedElement by remember {
            mutableStateOf(
                if (touchExplorationServicesEnabled)
                    FocusableElementDatePicker.NONE else FocusableElementDatePicker.DAY
            )
        }
        val focusRequesterDay = remember { FocusRequester() }
        val focusRequesterMonth = remember { FocusRequester() }
        val focusRequesterYear = remember { FocusRequester() }
        val focusRequesterConfirmButton = remember { FocusRequester() }

        LaunchedEffect(
            datePickerState.yearState.selectedOption,
            datePickerState.monthState.selectedOption
        ) {
            if (datePickerState.numOfMonths != datePickerState.monthState.numberOfOptions) {
                datePickerState.monthState.numberOfOptions = datePickerState.numOfMonths
            }
            if (datePickerState.numOfDays != datePickerState.dayState.numberOfOptions) {
                if (datePickerState.dayState.selectedOption >= datePickerState.numOfDays) {
                    datePickerState.dayState.animateScrollToOption(datePickerState.numOfDays - 1)
                }
                datePickerState.dayState.numberOfOptions = datePickerState.numOfDays
            }
        }
        val shortMonthNames = remember { getMonthNames("MMM") }
        val fullMonthNames = remember { getMonthNames("MMMM") }
        val yearContentDescription by remember(focusedElement, datePickerState.currentYear()) {
            derivedStateOf {
                createDescriptionDatePicker(
                    focusedElement,
                    datePickerState.currentYear(),
                    "${datePickerState.currentYear()}"
                )
            }
        }
        val monthContentDescription by remember(focusedElement, datePickerState.currentMonth()) {
            derivedStateOf {
                createDescriptionDatePicker(
                    focusedElement,
                    datePickerState.currentMonth(),
                    fullMonthNames[(datePickerState.currentMonth() - 1) % 12]
                )
            } }
        val dayContentDescription by remember(focusedElement, datePickerState.currentDay()) {
            derivedStateOf {
                createDescriptionDatePicker(
                    focusedElement, datePickerState.currentDay(), "${datePickerState.currentDay()}"
                )
            } }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (touchExplorationServicesEnabled) {
                        when (focusedElement) {
                            FocusableElementDatePicker.DAY ->
                                Modifier.scrollablePicker(datePickerState.dayState)

                            FocusableElementDatePicker.MONTH ->
                                Modifier.scrollablePicker(datePickerState.monthState)

                            FocusableElementDatePicker.YEAR ->
                                Modifier.scrollablePicker(datePickerState.yearState)

                            else -> Modifier
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val boxConstraints = this
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when (focusedElement) {
                        FocusableElementDatePicker.DAY -> "Day"
                        FocusableElementDatePicker.MONTH -> "Month"
                        FocusableElementDatePicker.YEAR -> "Year"
                        else -> ""
                    },
                    color = MaterialTheme.colors.secondary,
                    style = MaterialTheme.typography.button,
                    maxLines = 1,
                )
                val weightsToCenterVertically = 0.5f
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(weightsToCenterVertically)
                )
                val spacerWidth = 8.dp
                val dayWidth = 54.dp
                val monthWidth = 80.dp
                val yearWidth = 128.dp
                val doubleTapToNext = {
                        position: FocusableElementDatePicker, next: FocusableElementDatePicker ->
                    focusedElement = when (focusedElement) {
                        position -> next
                        else -> position
                    }
                }
                val offset = when (focusedElement) {
                    FocusableElementDatePicker.DAY -> (boxConstraints.maxWidth - dayWidth) / 2
                    FocusableElementDatePicker.MONTH ->
                        (boxConstraints.maxWidth - monthWidth) / 2 - dayWidth - spacerWidth
                    FocusableElementDatePicker.NONE -> (boxConstraints.maxWidth - dayWidth) / 2
                    else -> (boxConstraints.maxWidth - yearWidth) / 2 - monthWidth
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(offset)
                ) {
                    if (focusedElement.index < 2) {
                        DatePickerImpl(
                            state = datePickerState.dayState,
                            readOnly = focusedElement != FocusableElementDatePicker.DAY,
                            onSelected = {
                                doubleTapToNext(
                                    FocusableElementDatePicker.DAY,
                                    FocusableElementDatePicker.MONTH
                                )
                            },
                            text = { day: Int -> "%d".format(datePickerState.currentDay(day)) },
                            width = dayWidth,
                            focusRequester = focusRequesterDay,
                            contentDescription = dayContentDescription,
                            userScrollEnabled = !touchExplorationServicesEnabled ||
                                focusedElement == FocusableElementDatePicker.DAY,
                            touchExplorationServicesEnabled = touchExplorationServicesEnabled
                        )
                        Spacer(modifier = Modifier.width(spacerWidth))
                    }
                    DatePickerImpl(
                        state = datePickerState.monthState,
                        readOnly = focusedElement != FocusableElementDatePicker.MONTH,
                        onSelected = {
                            doubleTapToNext(
                                FocusableElementDatePicker.MONTH,
                                FocusableElementDatePicker.YEAR
                            )
                        },
                        text = { month: Int ->
                            shortMonthNames[(datePickerState.currentMonth(month) - 1) % 12] },
                        width = monthWidth,
                        focusRequester = focusRequesterMonth,
                        contentDescription = monthContentDescription,
                        userScrollEnabled = !touchExplorationServicesEnabled ||
                            focusedElement == FocusableElementDatePicker.MONTH,
                        touchExplorationServicesEnabled = touchExplorationServicesEnabled
                    )
                    if (focusedElement.index > 0) {
                        Spacer(modifier = Modifier.width(spacerWidth))
                        DatePickerImpl(
                            state = datePickerState.yearState,
                            readOnly = focusedElement != FocusableElementDatePicker.YEAR,
                            onSelected = {
                                doubleTapToNext(
                                    FocusableElementDatePicker.YEAR,
                                    FocusableElementDatePicker.CONFIRM_BUTTON
                                )
                            },
                            text = { year: Int -> "%4d".format(datePickerState.currentYear(year)) },
                            width = yearWidth,
                            focusRequester = focusRequesterYear,
                            contentDescription = yearContentDescription,
                            userScrollEnabled = !touchExplorationServicesEnabled ||
                                focusedElement == FocusableElementDatePicker.YEAR,
                            touchExplorationServicesEnabled = touchExplorationServicesEnabled
                        )
                    }
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .weight(weightsToCenterVertically)
                )
                Button(
                    onClick = {
                        if (focusedElement.index >= 2) {
                            val confirmedYear: Int = datePickerState.currentYear()
                            val confirmedMonth: Int = datePickerState.currentMonth()
                            val confirmedDay: Int = datePickerState.currentDay()
                            val confirmedDate =
                                LocalDate.of(confirmedYear, confirmedMonth, confirmedDay)
                            onDateConfirm(confirmedDate)
                        } else if (focusedElement == FocusableElementDatePicker.DAY) {
                            doubleTapToNext(
                                FocusableElementDatePicker.DAY,
                                FocusableElementDatePicker.MONTH
                            )
                        } else if (focusedElement == FocusableElementDatePicker.MONTH) {
                            doubleTapToNext(
                                FocusableElementDatePicker.MONTH,
                                FocusableElementDatePicker.YEAR
                            )
                        }
                    },
                    modifier = Modifier
                        .semantics {
                            focused = focusedElement == FocusableElementDatePicker.CONFIRM_BUTTON
                        }
                        .focusRequester(focusRequesterConfirmButton)
                ) {
                    DemoIcon(
                        resourceId =
                        if (focusedElement.index < 2) R.drawable.ic_chevron_right_24
                        else R.drawable.ic_check_24px,
                        contentDescription =
                        if (focusedElement.index >= 2) "confirm"
                        else "next"
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            LaunchedEffect(focusedElement) {
                if (focusedElement != FocusableElementDatePicker.NONE) {
                    listOf(
                        focusRequesterDay, focusRequesterMonth, focusRequesterYear,
                        focusRequesterConfirmButton
                    )[focusedElement.index].requestFocus()
                }
            }
        }
    }
}

@Composable
private fun DatePickerImpl(
    state: PickerState,
    readOnly: Boolean,
    onSelected: () -> Unit,
    text: (option: Int) -> String,
    focusRequester: FocusRequester,
    width: Dp,
    contentDescription: String,
    userScrollEnabled: Boolean,
    touchExplorationServicesEnabled: Boolean
) {
    PickerWithRSB(
        readOnly = readOnly,
        state = state,
        focusRequester = focusRequester,
        modifier = Modifier.size(width, 100.dp),
        onSelected = onSelected,
        contentDescription = contentDescription,
        userScrollEnabled = userScrollEnabled
    ) { option ->
        TimePiece(
            selected = !readOnly,
            onSelected = onSelected,
            text = text(option),
            style = MaterialTheme.typography.display2,
            touchExplorationServicesEnabled = touchExplorationServicesEnabled
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TimePiece(
    selected: Boolean,
    onSelected: () -> Unit,
    text: String,
    style: TextStyle,
    touchExplorationServicesEnabled: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val modifier = Modifier
            .align(Alignment.Center)
            .wrapContentSize()
        Text(
            text = text,
            maxLines = 1,
            style = style,
            color =
            if (selected) MaterialTheme.colors.secondary
            else MaterialTheme.colors.onBackground,
            modifier =
            if (selected || touchExplorationServicesEnabled) modifier
            else modifier.pointerInteropFilter {
                if (it.action == MotionEvent.ACTION_DOWN) onSelected()
                true
            },
        )
    }
}

@Composable
private fun Separator(width: Dp, textStyle: TextStyle) {
    Spacer(Modifier.width(width))
    Text(
        text = ":",
        style = textStyle,
        color = MaterialTheme.colors.onBackground,
        modifier = Modifier.clearAndSetSemantics {}
    )
    Spacer(Modifier.width(width))
}

@Composable fun PickerWithRSB(
    state: PickerState,
    readOnly: Boolean,
    modifier: Modifier,
    focusRequester: FocusRequester,
    contentDescription: String?,
    readOnlyLabel: @Composable (BoxScope.() -> Unit)? = null,
    userScrollEnabled: Boolean = true,
    flingBehavior: FlingBehavior = PickerDefaults.flingBehavior(state = state),
    onSelected: () -> Unit = {},
    option: @Composable PickerScope.(optionIndex: Int) -> Unit
) {
    Picker(
        state = state,
        modifier = if (userScrollEnabled) {
            modifier.rsbScroll(
                scrollableState = state,
                flingBehavior = flingBehavior,
                focusRequester = focusRequester,
            )
        } else modifier,
        flingBehavior = flingBehavior,
        readOnly = readOnly,
        readOnlyLabel = readOnlyLabel,
        userScrollEnabled = userScrollEnabled,
        onSelected = onSelected,
        contentDescription = contentDescription,
        option = option
    )
}

@Composable
fun PickerWithoutGradient() {
    val items = listOf("One", "Two", "Three", "Four", "Five")
    val state = rememberPickerState(items.size)
    val contentDescription by remember { derivedStateOf {
        "${state.selectedOption + 1}"
    } }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Picker(
            readOnly = false,
            modifier = Modifier.size(100.dp, 100.dp),
            gradientRatio = 0.0f,
            contentDescription = contentDescription,
            state = state
        ) {
            Text(items[it])
        }
    }
}

private fun pickerTextOption(textStyle: TextStyle, indexToText: (Int) -> String):
    (@Composable PickerScope.(optionIndex: Int, pickerSelected: Boolean) -> Unit) = {
        value: Int, pickerSelected: Boolean ->
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = indexToText(value),
            maxLines = 1,
            style = textStyle,
            color =
            if (pickerSelected) MaterialTheme.colors.secondary
            else MaterialTheme.colors.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize()
        )
    }
}

private fun Modifier.scrollablePicker(
    pickerState: PickerState
) = Modifier.composed {
    this.scrollable(
        state = pickerState,
        orientation = Orientation.Vertical,
        flingBehavior = PickerDefaults.flingBehavior(state = pickerState),
        reverseDirection = true
    )
}

private fun createDescription(
    pickerGroupState: PickerGroupState,
    selectedValue: Int,
    label: String
): String {
    return when (pickerGroupState.selectedIndex) {
        -1 -> label
        else -> "$selectedValue" + label
    }
}

private fun createDescription12Hour(
    focusedElement: FocusableElement12Hour,
    selectedValue: Int,
    label: String
): String {
    return when (focusedElement) {
        FocusableElement12Hour.HOURS -> {
            "$selectedValue" + label
        }
        FocusableElement12Hour.MINUTES -> {
            "$selectedValue" + label
        }
        else -> label
    }
}

private fun createDescriptionDatePicker(
    focusedElement: FocusableElementDatePicker,
    selectedValue: Int,
    label: String
): String {
    return when (focusedElement) {
        FocusableElementDatePicker.DAY -> {
            "$selectedValue"
        }
        FocusableElementDatePicker.YEAR -> {
            "$selectedValue"
        }
        else -> label
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun verifyDates(
    date: LocalDate,
    fromDate: LocalDate,
    toDate: LocalDate
) {
    require(toDate >= fromDate) { "toDate should be greater than or equal to fromDate" }
    require(date in fromDate..toDate) { "date should lie between fromDate and toDate" }
}

@SuppressLint("ClassVerificationFailure")
@RequiresApi(Build.VERSION_CODES.O)
private fun getMonthNames(pattern: String): List<String> {
    val monthFormatter = DateTimeFormatter.ofPattern(pattern)
    val months = 1..12
    return months.map {
        LocalDate.of(2022, it, 1).format(monthFormatter)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class DatePickerState constructor(
    private val date: LocalDate,
    private val fromDate: LocalDate?,
    private val toDate: LocalDate?
) {
    private val yearOffset = fromDate?.year ?: 1
    val numOfYears = (toDate?.year ?: 3000) - (yearOffset - 1)
    val yearState =
        PickerState(
            initialNumberOfOptions = numOfYears,
            initiallySelectedOption = date.year - yearOffset
        )
    val selectedYearEqualsFromYear: Boolean
        get() = (yearState.selectedOption == 0)
    val selectedYearEqualsToYear: Boolean
        get() = (yearState.selectedOption == yearState.numberOfOptions - 1)

    private val monthOffset: Int
        get() = (if (selectedYearEqualsFromYear) (fromDate?.monthValue ?: 1) else 1)
    private val maxMonths: Int
        get() = (if (selectedYearEqualsToYear) (toDate?.monthValue ?: 12) else 12)
    val numOfMonths: Int
        get() = (maxMonths.minus(monthOffset - 1))
    val monthState =
        PickerState(
            initialNumberOfOptions = numOfMonths,
            initiallySelectedOption = date.monthValue - monthOffset
        )
    val selectedMonthEqualsFromMonth: Boolean
        get() = (selectedYearEqualsFromYear && monthState.selectedOption == 0)
    val selectedMonthEqualsToMonth: Boolean
        get() = (selectedYearEqualsToYear &&
            monthState.selectedOption == monthState.numberOfOptions - 1)

    private val dayOffset: Int
        get() = (if (selectedMonthEqualsFromMonth) (fromDate?.dayOfMonth ?: 1) else 1)
    private val firstDayOfMonth: LocalDate
        get() = LocalDate.of(
            currentYear(),
            currentMonth(),
            1
        )
    private val maxDaysInMonth: Int
        get() = (
            if (toDate != null && selectedMonthEqualsToMonth) {
                toDate.dayOfMonth
            } else {
                firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
            }
            )
    val numOfDays: Int
        get() = maxDaysInMonth.minus(dayOffset - 1)

    val dayState =
        PickerState(
            initialNumberOfOptions = numOfDays,
            initiallySelectedOption = date.dayOfMonth - dayOffset
        )

    fun currentYear(year: Int = yearState.selectedOption): Int {
        return year + yearOffset
    }
    fun currentMonth(month: Int = monthState.selectedOption): Int {
        return month + monthOffset
    }
    fun currentDay(day: Int = dayState.selectedOption): Int {
        return day + dayOffset
    }
}

/**
 * This is copied from [TouchExplorationStateProvider]. Please updated if something changes over there.
 */
private class DefaultTouchExplorationStateProvider : TouchExplorationStateProvider {

    @Composable
    public override fun touchExplorationState(): State<Boolean> {
        val context = LocalContext.current
        val accessibilityManager = remember {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        }

        val listener = remember { Listener() }

        LocalLifecycleOwner.current.lifecycle.ObserveState(
            handleEvent = { event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    listener.register(accessibilityManager)
                }
            },
            onDispose = {
                listener.unregister(accessibilityManager)
            }
        )

        return remember { derivedStateOf { listener.isEnabled() } }
    }

    @Composable
    private fun Lifecycle.ObserveState(
        handleEvent: (Lifecycle.Event) -> Unit = {},
        onDispose: () -> Unit = {}
    ) {
        DisposableEffect(this) {
            val observer = LifecycleEventObserver { _, event ->
                handleEvent(event)
            }
            this@ObserveState.addObserver(observer)
            onDispose {
                onDispose()
                this@ObserveState.removeObserver(observer)
            }
        }
    }

    private class Listener : AccessibilityStateChangeListener, TouchExplorationStateChangeListener {
        private var accessibilityEnabled by mutableStateOf(false)
        private var touchExplorationEnabled by mutableStateOf(false)

        fun isEnabled() = accessibilityEnabled && touchExplorationEnabled

        override fun onAccessibilityStateChanged(it: Boolean) {
            accessibilityEnabled = it
        }

        override fun onTouchExplorationStateChanged(it: Boolean) {
            touchExplorationEnabled = it
        }

        fun register(am: AccessibilityManager) {
            accessibilityEnabled = am.isEnabled
            touchExplorationEnabled = am.isTouchExplorationEnabled

            am.addTouchExplorationStateChangeListener(this)
            am.addAccessibilityStateChangeListener(this)
        }

        fun unregister(am: AccessibilityManager) {
            am.removeTouchExplorationStateChangeListener(this)
            am.removeAccessibilityStateChangeListener(this)
        }
    }
}

enum class FocusableElement12Hour(val index: Int) {
    HOURS(0),
    MINUTES(1),
    PERIOD(2),
    CONFIRM_BUTTON(3),
    NONE(-1)
}

enum class FocusableElementDatePicker(val index: Int) {
    DAY(0),
    MONTH(1),
    YEAR(2),
    CONFIRM_BUTTON(3),
    NONE(-1)
}
