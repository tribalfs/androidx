/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.support.annotation.RequiresApi;
import android.view.KeyEvent;

@RequiresApi(18)
interface TransportMediatorCallback {
    public void handleKey(KeyEvent key);
    public void handleAudioFocusChange(int focusChange);
    public long getPlaybackPosition();
    public void playbackPositionUpdate(long newPositionMs);
}
