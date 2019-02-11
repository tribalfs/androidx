/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.hardware.camera2.CameraCaptureSession;
import android.os.Build;
import android.view.Surface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class CameraCaptureSessionStateCallbacksAndroidTest {

    @Test
    public void comboCallbackInvokesConstituentCallbacks() {
        CameraCaptureSession.StateCallback callback0 =
                Mockito.mock(CameraCaptureSession.StateCallback.class);
        CameraCaptureSession.StateCallback callback1 =
                Mockito.mock(CameraCaptureSession.StateCallback.class);
        CameraCaptureSession.StateCallback comboCallback =
                CameraCaptureSessionStateCallbacks.createComboCallback(callback0, callback1);
        CameraCaptureSession session = Mockito.mock(CameraCaptureSession.class);
        Surface surface = Mockito.mock(Surface.class);

        comboCallback.onConfigured(session);
        verify(callback0, times(1)).onConfigured(session);
        verify(callback1, times(1)).onConfigured(session);

        comboCallback.onActive(session);
        verify(callback0, times(1)).onActive(session);
        verify(callback1, times(1)).onActive(session);

        comboCallback.onClosed(session);
        verify(callback0, times(1)).onClosed(session);
        verify(callback1, times(1)).onClosed(session);

        comboCallback.onReady(session);
        verify(callback0, times(1)).onReady(session);
        verify(callback1, times(1)).onReady(session);

        if (Build.VERSION.SDK_INT >= 26) {
            comboCallback.onCaptureQueueEmpty(session);
            verify(callback0, times(1)).onCaptureQueueEmpty(session);
            verify(callback1, times(1)).onCaptureQueueEmpty(session);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            comboCallback.onSurfacePrepared(session, surface);
            verify(callback0, times(1)).onSurfacePrepared(session, surface);
            verify(callback1, times(1)).onSurfacePrepared(session, surface);
        }

        comboCallback.onConfigureFailed(session);
        verify(callback0, times(1)).onConfigureFailed(session);
        verify(callback1, times(1)).onConfigureFailed(session);
    }
}
