/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.support.v17.leanback.transition;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.support.annotation.RestrictTo;

/**
 * Listeners for transition start and stop.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class TransitionListener {

    protected Object mImpl;

    public void onTransitionStart(Object transition) {
    }

    public void onTransitionEnd(Object transition) {
    }

    public void onTransitionCancel(Object transition) {
    }

    public void onTransitionPause(Object transition) {
    }

    public void onTransitionResume(Object transition) {
    }
}
