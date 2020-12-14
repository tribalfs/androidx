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

package androidx.work;

import static androidx.annotation.experimental.Experimental.Level.ERROR;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.experimental.Experimental;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An API surface for immediate {@link WorkRequest}s.
 */
@Retention(CLASS)
@Target({TYPE, METHOD, PACKAGE})
@Experimental(level = ERROR)
public @interface ExperimentalImmediateWork {

}
