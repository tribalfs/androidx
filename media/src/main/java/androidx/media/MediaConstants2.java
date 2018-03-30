/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.media;

class MediaConstants2 {

    static final int CONNECT_RESULT_CONNECTED = 0;
    static final int CONNECT_RESULT_DISCONNECTED = -1;

    static final String CUSTOM_COMMAND_CONNECT = "androidx.media.command.CONNECT";

    static final String ARGUMENT_ALLOWED_COMMANDS = "androidx.media.argument.ALLOWED_COMMANDS";
    static final String ARGUMENT_PLAYER_STATE = "androidx.media.argument.PLAYER_STATE";
    static final String ARGUMENT_REPEAT_MODE = "androidx.media.argument.REPEAT_MODE";
    static final String ARGUMENT_SHUFFLE_MODE = "androidx.media.argument.SHUFFLE_MODE";
    static final String ARGUMENT_PLAYLIST = "androidx.media.argument.PLAYLIST";
    static final String ARGUMENT_PLAYBACK_STATE_COMPAT =
            "androidx.media.argument.PLAYBACK_STATE_COMPAT";
    static final String ARGUMENT_MEDIA_METADATA_COMPAT =
            "androidx.media.argument.MEDIA_METADATA_COMPAT";
    static final String ARGUMENT_ICONTROLLER_CALLBACK =
            "androidx.media.argument.ICONTROLLER_CALLBACK";
    static final String ARGUMENT_UID = "androidx.media.argument.UID";
    static final String ARGUMENT_PID = "androidx.media.argument.PID";
    static final String ARGUMENT_PACKAGE_NAME = "androidx.media.argument.PACKAGE_NAME";

    private MediaConstants2() {
    }
}
