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

package androidx.camera.camera2.impl.compat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP, shadows = {
        CameraManagerCompatTest.ShadowInteractionCameraManager.class})
public final class CameraManagerCompatTest {

    private static final String CAMERA_ID = "0";

    private Context mContext;
    private ShadowInteractionCameraManager.Callback mInteractionCallback;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;

        mContext = ApplicationProvider.getApplicationContext();

        // Install the mock camera manager
        mInteractionCallback = mock(ShadowInteractionCameraManager.Callback.class);
        ShadowInteractionCameraManager shadowManager = Shadow.extract(
                mContext.getSystemService(Context.CAMERA_SERVICE));
        shadowManager.addCallback(mInteractionCallback);
    }

    @Test
    @Config(maxSdk = 27)
    public void openCamera_callsHandlerMethod() throws CameraAccessException {
        CameraManagerCompat manager = CameraManagerCompat.from(mContext);
        manager.openCamera(CAMERA_ID, mock(Executor.class),
                mock(CameraDevice.StateCallback.class));

        verify(mInteractionCallback, times(1)).openCamera(any(String.class),
                any(CameraDevice.StateCallback.class), any(Handler.class));
    }

    @Test
    @Config(minSdk = 28)
    public void openCamera_callsExecutorMethod() throws CameraAccessException {
        CameraManagerCompat manager = CameraManagerCompat.from(mContext);
        manager.openCamera(CAMERA_ID, mock(Executor.class),
                mock(CameraDevice.StateCallback.class));

        verify(mInteractionCallback, times(1)).openCamera(any(String.class),
                any(Executor.class), any(CameraDevice.StateCallback.class));
    }

    @Test
    public void unwrap_allowsAccessToUnderlyingMethods() throws CameraAccessException {
        CameraManagerCompat manager = CameraManagerCompat.from(mContext);
        manager.unwrap().getCameraIdList();

        verify(mInteractionCallback, times(1)).getCameraIdList();
    }

    /**
     * A Shadow of {@link CameraManager} which forwards invocations to callbacks to record
     * interactions.
     */
    @Implements(
            value = CameraManager.class,
            minSdk = 21
    )
    static final class ShadowInteractionCameraManager {

        private static final String[] EMPTY_ID_LIST = new String[]{};
        private final List<Callback> mCallbacks = new ArrayList<>();

        void addCallback(Callback callback) {
            mCallbacks.add(callback);
        }

        @NonNull
        @Implementation
        protected String[] getCameraIdList() throws CameraAccessException {
            for (Callback cb : mCallbacks) {
                String[] ids = cb.getCameraIdList();
            }

            return EMPTY_ID_LIST;
        }

        @Implementation
        protected void openCamera(@NonNull String cameraId,
                @NonNull CameraDevice.StateCallback callback, @Nullable Handler handler) {
            for (Callback cb : mCallbacks) {
                cb.openCamera(cameraId, callback, handler);
            }
        }

        @Implementation
        protected void openCamera(@NonNull String cameraId,
                @NonNull Executor executor,
                @NonNull CameraDevice.StateCallback callback) {
            for (Callback cb : mCallbacks) {
                cb.openCamera(cameraId, executor, callback);
            }
        }

        interface Callback {
            @NonNull
            String[] getCameraIdList();

            void openCamera(@NonNull String cameraId,
                    @NonNull CameraDevice.StateCallback callback, @Nullable Handler handler);

            void openCamera(@NonNull String cameraId,
                    @NonNull Executor executor,
                    @NonNull CameraDevice.StateCallback callback);
        }
    }
}
