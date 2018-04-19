/*
 * Copyright 2018 The Android Open Source Project
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

import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.Arrays;
import java.util.List;

/**
 * An opaque class that allows you to chain together {@link OneTimeWorkRequest}.
 */
public abstract class WorkContinuation {

    /**
     * Adds new {@link OneTimeWorkRequest} items that depend on the successful completion of
     * all previously added {@link OneTimeWorkRequest}.
     *
     * @param work One or more {@link OneTimeWorkRequest} to add to the {@link WorkContinuation}
     * @return A {@link WorkContinuation} that allows for further chaining of dependent
     *         {@link OneTimeWorkRequest}
     */
    public final WorkContinuation then(@NonNull OneTimeWorkRequest... work) {
        return then(Arrays.asList(work));
    }

    /**
     * Adds new {@link OneTimeWorkRequest} items that depend on the successful completion
     * of all previously added {@link OneTimeWorkRequest}.
     *
     * @param work One or more {@link OneTimeWorkRequest} to add to the {@link WorkContinuation}
     * @return A {@link WorkContinuation} that allows for further chaining of dependent
     *         {@link OneTimeWorkRequest}
     */
    public abstract WorkContinuation then(@NonNull List<OneTimeWorkRequest> work);

    /**
     * Returns a {@link LiveData} list of {@link WorkStatus} that provides information about work,
     * their progress, and any resulting output.  If state or outputs of any of the jobs in this
     * chain changes, any attached {@link android.arch.lifecycle.Observer}s will trigger.
     *
     * @return A {@link LiveData} containing a list of {@link WorkStatus}es
     */
    public abstract LiveData<List<WorkStatus>> getStatuses();

    /**
     * Enqueues the instance of {@link WorkContinuation} on the background thread.
     */
    public abstract void enqueue();

    /**
     * Gets an object that gives access to blocking (synchronous) methods.
     *
     * @return A {@link BlockingWorkContinuation} object, which gives access to blocking
     *         (synchronous) methods
     */
    public abstract BlockingWorkContinuation blocking();

    /**
     * Joins multiple {@link WorkContinuation}s to allow for complex chaining.
     *
     * @param continuations Two or more {@link WorkContinuation}s that are prerequisites for the
     *                      return value
     * @return A {@link WorkContinuation} that allows further chaining
     */
    public static WorkContinuation join(@NonNull WorkContinuation... continuations) {
        return join(Arrays.asList(continuations));
    }

    /**
     * Joins multiple {@link WorkContinuation}s to allow for complex chaining.
     *
     * @param continuations Two or more {@link WorkContinuation}s that are prerequisites for the
     *                      return value
     * @return A {@link WorkContinuation} that allows further chaining
     */
    public static WorkContinuation join(@NonNull List<WorkContinuation> continuations) {
        if (continuations.size() < 2) {
            throw new IllegalArgumentException(
                    "WorkContinuation.join() needs at least 2 continuations.");
        }

        return continuations.get(0).joinInternal(null, continuations);
    }

    /**
     * Joins multiple {@link WorkContinuation}s to allow for complex chaining using the
     * {@link OneTimeWorkRequest} provided.
     *
     * @param work The {@link OneTimeWorkRequest} which depends on the successful completion of the
     *             provided {@link WorkContinuation}s
     * @param continuations Two or more {@link WorkContinuation}s that are prerequisites for the
     *                      {@link OneTimeWorkRequest} provided.
     * @return A {@link WorkContinuation} that allows further chaining
     */
    public static WorkContinuation join(
            @NonNull OneTimeWorkRequest work,
            @NonNull WorkContinuation... continuations) {
        return join(work, Arrays.asList(continuations));
    }

    /**
     * Joins multiple {@link WorkContinuation}s to allow for complex chaining using the
     * {@link OneTimeWorkRequest} provided.
     *
     * @param work The {@link OneTimeWorkRequest} which depends on the successful completion of the
     *             provided {@link WorkContinuation}s
     * @param continuations Two or more {@link WorkContinuation}s that are prerequisites for the
     *                      {@link OneTimeWorkRequest} provided.
     * @return A {@link WorkContinuation} that allows further chaining
     */
    public static WorkContinuation join(
            @NonNull OneTimeWorkRequest work,
            @NonNull List<WorkContinuation> continuations) {
        return continuations.get(0).joinInternal(work, continuations);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract WorkContinuation joinInternal(
            @Nullable OneTimeWorkRequest work,
            @NonNull List<WorkContinuation> continuations);
}
