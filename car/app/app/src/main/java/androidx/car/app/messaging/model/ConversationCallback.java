/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.car.app.messaging.model;

import androidx.annotation.NonNull;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.ExperimentalCarApi;

/** Host -> Client callbacks for a {@link ConversationItem} */
@ExperimentalCarApi
@CarProtocol
public interface ConversationCallback {
    /**
     * Notifies the app that it should mark all messages in the current conversation as read
     */
    void onMarkAsRead();

    /**
     * Notifies the app that it should send a reply to a given conversation
     */
    void onTextReply(@NonNull String replyText);
}
