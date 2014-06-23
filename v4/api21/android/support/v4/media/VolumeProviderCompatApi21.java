/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v4.media;

import android.media.VolumeProvider;

class VolumeProviderCompatApi21 {
    public static Object createVolumeProvider(int volumeControl, int maxVolume,
            final Delegate delegate) {
        return new VolumeProvider(volumeControl, maxVolume) {
            @Override
            public int onGetCurrentVolume() {
                return delegate.onGetCurrentVolume();
            }

            @Override
            public void onSetVolumeTo(int volume) {
                delegate.onSetVolumeTo(volume);
            }

            @Override
            public void onAdjustVolumeBy(int delta) {
                delegate.onAdjustVolumeBy(delta);
            }
        };
    }

    public static void notifyVolumeChanged(Object volumeProviderObj) {
        ((VolumeProvider)volumeProviderObj).notifyVolumeChanged();
    }

    public interface Delegate {
        int onGetCurrentVolume();
        void onSetVolumeTo(int volume);
        void onAdjustVolumeBy(int delta);
    }
}
