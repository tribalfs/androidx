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

package androidx.window;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Tests for {@link WindowBackend} class. */
@SuppressWarnings("unchecked")
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class WindowBackendTest extends WindowTestBase {

    /**
     * Verifies that {@link WindowManager} instance would use the assigned
     * {@link WindowBackend}.
     */
    @Test
    public void testFakeWindowBackend() {
        WindowLayoutInfo windowLayoutInfo = newTestWindowLayout();
        DeviceState deviceState = newTestDeviceState();
        WindowBackend windowBackend = new FakeWindowBackend(windowLayoutInfo, deviceState);
        TestActivity activity = mActivityTestRule.launchActivity(new Intent());
        WindowManager wm = new WindowManager(activity, windowBackend);
        Consumer<WindowLayoutInfo> layoutInfoConsumer = mock(Consumer.class);
        Consumer<DeviceState> stateConsumer = mock(Consumer.class);

        wm.registerLayoutChangeCallback(MoreExecutors.directExecutor(), layoutInfoConsumer);
        wm.registerDeviceStateChangeCallback(MoreExecutors.directExecutor(), stateConsumer);

        verify(layoutInfoConsumer).accept(windowLayoutInfo);
        verify(stateConsumer).accept(deviceState);
    }

    private WindowLayoutInfo newTestWindowLayout() {
        List<DisplayFeature> displayFeatureList = new ArrayList<>();
        DisplayFeature displayFeature = new FoldingFeature(
                new Rect(10, 0, 10, 100), FoldingFeature.TYPE_HINGE,
                FoldingFeature.STATE_FLAT);
        displayFeatureList.add(displayFeature);
        return new WindowLayoutInfo(displayFeatureList);
    }

    private DeviceState newTestDeviceState() {
        return new DeviceState(DeviceState.POSTURE_OPENED);
    }

    private static class FakeWindowBackend implements WindowBackend {
        private WindowLayoutInfo mWindowLayoutInfo;
        private DeviceState mDeviceState;

        private FakeWindowBackend(@NonNull WindowLayoutInfo windowLayoutInfo,
                @NonNull DeviceState deviceState) {
            mWindowLayoutInfo = windowLayoutInfo;
            mDeviceState = deviceState;
        }


        /**
         * @deprecated will be removed in next alpha
         * @return nothing, throws an exception.
         */
        @Override
        @NonNull
        @Deprecated // TODO(b/173739071) Remove in next alpha.
        public DeviceState getDeviceState() {
            throw new RuntimeException("Deprecated method");
        }

        /**
         * @deprecated will be removed in next alpha
         * @param activity any {@link Activity}
         * @return nothing, throws an exception since this is depredcated
         */
        @Override
        @NonNull
        @Deprecated // TODO(b/173739071) Remove in next alpha.
        public WindowLayoutInfo getWindowLayoutInfo(@NonNull Activity activity) {
            throw new RuntimeException("Deprecated method");
        }

        /**
         * @deprecated will be removed in next alpha
         * @param context any {@link Context}
         * @return nothing, throws an exception since this is deprecated.
         */
        @NonNull
        @Override
        @Deprecated // TODO(b/173739071) Remove in next alpha.
        public WindowLayoutInfo getWindowLayoutInfo(@NonNull Context context) {
            throw new RuntimeException("Deprecated method");
        }

        /**
         * Throws an exception if used.
         * @deprecated will be removed in next alpha
         * @param context any {@link Activity}
         * @param executor any {@link Executor}
         * @param callback any {@link Consumer}
         */
        @Override
        @Deprecated // TODO(b/173739071) Remove in next alpha.
        public void registerLayoutChangeCallback(@NonNull Context context,
                @NonNull Executor executor, @NonNull Consumer<WindowLayoutInfo> callback) {
            throw new RuntimeException("Deprecated method");
        }

        @Override
        public void registerLayoutChangeCallback(@NonNull Activity activity,
                @NonNull Executor executor, @NonNull Consumer<WindowLayoutInfo> callback) {
            executor.execute(() -> callback.accept(mWindowLayoutInfo));
        }

        @Override
        public void unregisterLayoutChangeCallback(@NonNull Consumer<WindowLayoutInfo> callback) {
            // Empty
        }

        @Override
        public void registerDeviceStateChangeCallback(@NonNull Executor executor,
                @NonNull Consumer<DeviceState> callback) {
            executor.execute(() -> callback.accept(mDeviceState));
        }

        @Override
        public void unregisterDeviceStateChangeCallback(@NonNull Consumer<DeviceState> callback) {
            // Empty
        }
    }
}
