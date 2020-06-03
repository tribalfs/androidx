/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose

/**
 * When applied to a composable function [Direct] will prevent code from being generated which
 * allow this function's execution to be skipped or restarted. This may be desirable for small
 * functions which just directly call another composable function and have very little machinery
 * in them directly.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION
)
@Deprecated(
    "Use ComposableContract instead",
    replaceWith = ReplaceWith(
        "ComposableContract(restartable = false)",
        "androidx.compose.ComposableContract"
    ),
    level = DeprecationLevel.ERROR
)
annotation class Direct