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
package android.arch.background.workmanager;

import android.arch.background.workmanager.model.Constraints;
import android.arch.background.workmanager.model.WorkSpec;

/**
 * Generates test WorkSpecs.
 */

public class WorkSpecs {

    /**
     * Creates a {@link WorkSpec} from a {@link Worker} class for testing.  Used to overcome the
     * fact that Work.getWorkSpec is not public (nor should it be).
     *
     * @param clazz The {@link Worker} class
     * @return A {@link WorkSpec}
     */
    public static WorkSpec getWorkSpec(Class<? extends Worker> clazz) {
        return getWorkSpec(clazz, Work.STATUS_ENQUEUED);
    }

    /**
     * Creates a {@link WorkSpec} from a {@link Worker} class for testing.  Used to overcome the
     * fact that Work.getWorkSpec is not public (nor should it be).  This version also sets the
     * initial status.
     *
     * @param clazz The {@link Worker} class
     * @param status The initial {@link Work.WorkStatus}
     * @return A {@link WorkSpec}
     */
    public static WorkSpec getWorkSpec(Class<? extends Worker> clazz, @Work.WorkStatus int status) {
        Work work = new Work.Builder(clazz).withInitialStatus(status).build();
        return work.getWorkSpec();
    }

    /**
     * Creates a {@link WorkSpec} from a {@link Worker} class for testing.  Used to overcome the
     * fact that Work.getWorkSpec is not public (nor should it be).  This version also sets the
     * {@link Constraints}.
     *
     * @param clazz The {@link Worker} class
     * @param constraints The {@link Constraints} to set
     * @return A {@link WorkSpec}
     */
    public static WorkSpec getWorkSpec(Class<? extends Worker> clazz, Constraints constraints) {
        Work work = new Work.Builder(clazz).withConstraints(constraints).build();
        return work.getWorkSpec();
    }

    /**
     * Creates a {@link WorkSpec} from a {@link Worker} class for testing.  Used to overcome the
     * fact that Work.getWorkSpec is not public (nor should it be).  This version also sets the
     * tag.
     *
     * @param clazz The {@link Worker} class
     * @param tag The tag to set
     * @return A {@link WorkSpec}
     */
    public static WorkSpec getWorkSpecWithTag(Class<? extends Worker> clazz, String tag) {
        Work work = new Work.Builder(clazz).withTag(tag).build();
        return work.getWorkSpec();
    }

    private WorkSpecs() {
        // Do nothing.
    }
}
