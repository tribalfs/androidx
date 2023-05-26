/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.protolayout.expression.pipeline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Utility for time data source. */
class EpochTimePlatformDataSource {
    @NonNull private final MainThreadExecutor mExecutor = new MainThreadExecutor();

    @NonNull final List<DynamicTypeValueReceiverWithPreUpdate<Instant>> mConsumerToTimeCallback =
            new ArrayList<>();
    @Nullable private final PlatformTimeUpdateNotifier mUpdateNotifier;

    EpochTimePlatformDataSource(
            @Nullable PlatformTimeUpdateNotifier platformTimeUpdateNotifier) {
        this.mUpdateNotifier = platformTimeUpdateNotifier;
    }

    @UiThread
    void registerForData(DynamicTypeValueReceiverWithPreUpdate<Instant> consumer) {
        if (mConsumerToTimeCallback.isEmpty() && mUpdateNotifier != null) {
            mUpdateNotifier.setReceiver(this::tick);
        }
        mConsumerToTimeCallback.add(consumer);
    }

    @UiThread
    void unregisterForData(DynamicTypeValueReceiverWithPreUpdate<Instant> consumer) {
        mConsumerToTimeCallback.remove(consumer);
        if (mConsumerToTimeCallback.isEmpty() && mUpdateNotifier != null) {
            mUpdateNotifier.clearReceiver();
        }
    }

    /**
     * Updates all registered consumers with the new time.
     */
    @SuppressWarnings("NullAway")
    private ListenableFuture<Void> tick() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mExecutor.execute(() -> {
                try {
                    mConsumerToTimeCallback.forEach(
                            DynamicTypeValueReceiverWithPreUpdate::onPreUpdate);
                    Instant currentTime = Instant.now();
                    mConsumerToTimeCallback.forEach(c -> c.onData(currentTime));
                    completer.set(null);
                } catch (RuntimeException e) {
                    completer.setException(e);
                }
            });
            return "EpochTImePlatformDataSource#tick";
        });
    }

    @VisibleForTesting
    int getRegisterConsumersCount() {
        return mConsumerToTimeCallback.size();
    }
}
