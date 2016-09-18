/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.v4.media.session;

import android.media.session.MediaController;

class MediaControllerCompatApi25 {
    public static Object createCallback(Callback callback) {
        return new CallbackProxy<Callback>(callback);
    }

    public static int getRepeatMode(Object controllerObj) {
        return ((MediaController) controllerObj).getRepeatMode();
    }

    public static boolean isShuffleModeEnabled(Object controllerObj) {
        return ((MediaController) controllerObj).isShuffleModeEnabled();
    }

    public static class TransportControls extends MediaControllerCompatApi23.TransportControls {
        public static void setRepeatMode(Object controlsObj, int repeatMode) {
            ((MediaController.TransportControls) controlsObj).setRepeatMode(repeatMode);
        }

        public static void setShuffleModeEnabled(Object controlsObj, boolean enabled) {
            ((MediaController.TransportControls) controlsObj).setShuffleModeEnabled(enabled);
        }
    }

    public interface Callback extends MediaControllerCompatApi21.Callback {
        void onRepeatModeChanged(int repeatMode);
        void onShuffleModeChanged(boolean enabled);
    }

    static class CallbackProxy<T extends Callback> extends MediaControllerCompatApi21
            .CallbackProxy<T> {
        CallbackProxy(T callback) {
            super(callback);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            mCallback.onRepeatModeChanged(repeatMode);
        }

        @Override
        public void onShuffleModeChanged(boolean enabled) {
            mCallback.onShuffleModeChanged(enabled);
        }
    }
}
