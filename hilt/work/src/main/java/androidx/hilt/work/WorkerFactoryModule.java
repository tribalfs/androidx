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

package androidx.hilt.work;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.work.Worker;

import java.util.Map;

import javax.inject.Provider;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.multibindings.Multibinds;

/**
 * Hilt Modules for providing the Worker factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
@Module
@InstallIn(ApplicationComponent.class)
public abstract class WorkerFactoryModule {

    @NonNull
    @Multibinds
    abstract Map<String, WorkerAssistedFactory<? extends Worker>> workerFactoriesMap();

    @NonNull
    @Provides
    static WorkerFactory provideFactory(@NonNull Map<String,
            Provider<WorkerAssistedFactory<? extends Worker>>> workerFactories) {
        return new WorkerFactory(workerFactories);
    }
}
