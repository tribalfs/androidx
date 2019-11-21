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

package androidx.camera.integration.view;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.core.AppConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;

/** The application. */
public class ViewApplication extends Application implements AppConfig.Provider {
    @Override
    public void onCreate() {
        super.onCreate();

        // TODO(b/144888472): Initialize when needed instead of in Application#onCreate().
        ProcessCameraProvider.getInstance(this);
    }

    @NonNull
    @Override
    public AppConfig getAppConfig() {
        return Camera2AppConfig.create(this);
    }
}
