/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:Suppress("NOTHING_TO_INLINE") // Aliases to other public API.

package androidx.util

import android.annotation.SuppressLint
import android.support.annotation.HalfFloat
import android.support.annotation.RequiresApi
import android.util.Half

/**
 * Returns a [Half] instance representing given [Short].
 *
 * @see Half.valueOf
 */
// TODO https://youtrack.jetbrains.com/issue/KT-21696
@Suppress("WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET_ON_TYPE")
@SuppressLint("HalfFloat") // TODO remove https://issuetracker.google.com/issues/72509078
@RequiresApi(26)
inline fun @receiver:HalfFloat Short.toHalf(): Half = Half.valueOf(this)

/**
 * Returns a [Half] instance representing given [Float].
 *
 * @see Half.valueOf
 */
@RequiresApi(26)
inline fun Float.toHalf(): Half = Half.valueOf(this)

/**
 * Returns a [Half] instance representing given [Double].
 *
 * @see Half.valueOf
 */
@RequiresApi(26)
inline fun Double.toHalf(): Half = toFloat().toHalf()

/**
 * Returns a [Half] instance representing given [String].
 *
 * @see Half.valueOf
 */
@RequiresApi(26)
inline fun String.toHalf(): Half = Half.valueOf(this)
