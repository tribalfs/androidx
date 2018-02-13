/*
 * Copyright 2017 The Android Open Source Project
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
package android.arch.background.workmanager.impl;

import android.arch.background.workmanager.impl.model.WorkSpec;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * An interface for classes responsible for scheduling background work.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Scheduler {

    /**
     * Schedule the given {@link WorkSpec}s for background execution.  The Scheduler does NOT need
     * to check if there are any dependencies.
     *
     * @param workSpecs The array of {@link WorkSpec}s to schedule
     */
    void schedule(WorkSpec... workSpecs);

    /**
     * Cancel the work identified by the given {@link WorkSpec} id.
     *
     * @param workSpecId The id of the work to stopWork
     */
    void cancel(@NonNull String workSpecId);
}
