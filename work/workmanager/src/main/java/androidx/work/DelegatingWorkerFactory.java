/*
 * Copyright 2019 The Android Open Source Project
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedList;
import java.util.List;

/**
 * A {@link WorkerFactory} which delegates to other factories. Factories can register themselves
 * as delegates, and they will be invoked in order until a delegated factory returns a
 * non-null {@link ListenableWorker} instance.
 */
public class DelegatingWorkerFactory extends WorkerFactory {

    private static final String TAG = Logger.tagWithPrefix("DelegatingWkrFctry");

    private final List<WorkerFactory> mFactories;

    /**
     * Creates a new instance of the {@link DelegatingWorkerFactory}.
     */
    public DelegatingWorkerFactory() {
        mFactories = new LinkedList<>();
    }

    @VisibleForTesting
    @NonNull
    List<WorkerFactory> getFactories() {
        return mFactories;
    }

    /**
     * Adds a {@link WorkerFactory} to the list of delegates.
     *
     * @param workerFactory The {@link WorkerFactory} instance.
     */
    public final void addFactory(@NonNull WorkerFactory workerFactory) {
        mFactories.add(workerFactory);
    }

    @Override
    @Nullable
    public final ListenableWorker createWorker(@NonNull Context context,
            @NonNull String workerClass, @NonNull WorkerParameters parameters) {

        for (WorkerFactory factory : mFactories) {
            try {
                ListenableWorker worker = factory.createWorker(
                        context, workerClass, parameters);
                if (worker != null) {
                    return worker;
                }
            } catch (Throwable throwable) {
                String message =
                        String.format("Unable to instantiate a ListenableWorker (%s)", workerClass);
                Logger.get().error(TAG, message, throwable);
                throw throwable;
            }
        }
        // If none of the delegates can instantiate a ListenableWorker return null
        // so we can fallback to the default factory which is based on reflection.
        return null;
    }
}
