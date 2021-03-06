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

package androidx.wear.watchface.samples.minimal;

import android.view.SurfaceHolder;

import androidx.wear.watchface.Complication;
import androidx.wear.watchface.ComplicationsManager;
import androidx.wear.watchface.ListenableWatchFaceService;
import androidx.wear.watchface.Renderer;
import androidx.wear.watchface.WatchFace;
import androidx.wear.watchface.WatchFaceType;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.UserStyleRepository;
import androidx.wear.watchface.style.UserStyleSchema;
import androidx.wear.watchface.style.UserStyleSetting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/** The service that defines the watch face. */
public class WatchFaceService extends ListenableWatchFaceService {

    @NotNull
    @Override
    protected ListenableFuture<WatchFace> createWatchFaceFuture(
            @NotNull SurfaceHolder surfaceHolder, @NotNull WatchState watchState) {
        UserStyleRepository userStyleRepository =
                new UserStyleRepository(
                        new UserStyleSchema(Collections.<UserStyleSetting>emptyList()));
        ComplicationsManager complicationManager =
                new ComplicationsManager(Collections.<Complication>emptyList(),
                        userStyleRepository);
        Renderer renderer = new WatchFaceRenderer(surfaceHolder, userStyleRepository, watchState);
        return Futures.immediateFuture(
                new WatchFace(WatchFaceType.DIGITAL, userStyleRepository, renderer,
                        complicationManager));
    }
}
