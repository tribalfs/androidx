/*
 * Copyright (C) 2014 The Android Open Source Project
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

package androidx.test.uiautomator;

import android.app.UiAutomation.AccessibilityEventFilter;

/**
 * An {@link EventCondition} is a condition which depends on an event or series of events having
 * occurred.
 */
public abstract class EventCondition<U> implements AccessibilityEventFilter {

    /**
     * returns a value obtained after applying the condition to a series of events.
     * A non-null/non-false value indicates that the condition is satisfied.
     */
    public abstract U getResult();
}
