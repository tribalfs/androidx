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

package androidx.car.app.utils.annotations;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import androidx.annotation.RestrictTo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API as experimental.
 *
 * <p>By default, classes and all their inner members will be stripped from the library releases.
 * Update the proguard config file to keep this if the API(s) were to be included in an EAP, alpha
 * or beta version.
 *
 * <p>When this is used at the class-level, make sure that its inner members (e.g. fields or
 * methods) are NOT annotated with any keep annotations. Otherwise, proguard will look at those keep
 * annotations and decide to keep them, and as a result keeping the class as well.
 *
 * @hide
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
@RestrictTo(LIBRARY)
public @interface Experimental {
}
