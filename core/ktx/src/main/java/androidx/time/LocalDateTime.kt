/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package androidx.time

import android.support.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Return the [LocalDate] of this [LocalDateTime].
 *
 * @see LocalDateTime.toLocalDate
 */
@RequiresApi(26)
inline operator fun LocalDateTime.component1(): LocalDate = toLocalDate()

/**
 * Return the [LocalTime] of this [LocalDateTime].
 *
 * @see LocalDateTime.toLocalTime
 */
@RequiresApi(26)
inline operator fun LocalDateTime.component2(): LocalTime = toLocalTime()
