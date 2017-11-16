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
package android.arch.background.workmanager.constraints;

import android.arch.background.workmanager.model.WorkSpec;

import java.util.List;

/**
 * A callback for when constraints change.
 */

public interface ConstraintsMetCallback {
    /**
     * Called when all constraints are met.
     *
     * @param workSpecs The list of {@link WorkSpec}s that are eligible to run
     */
    void onAllConstraintsMet(List<WorkSpec> workSpecs);

    /**
     * Called when all constraints are not met.
     *
     * @param workSpecs The list of {@link WorkSpec}s that are not eligible to run
     */
    void onAllConstraintsNotMet(List<WorkSpec> workSpecs);
}
