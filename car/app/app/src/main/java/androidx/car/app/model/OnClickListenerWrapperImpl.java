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

package androidx.car.app.model;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.annotation.SuppressLint;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.car.app.IOnDoneCallback;
import androidx.car.app.OnDoneCallback;
import androidx.car.app.utils.RemoteUtils;

/**
 * Implementation class for {@link OnClickListenerWrapper} to allow IPC for click-related events.
 *
 * @hide
 */
// TODO(b/177591476): remove after host references have been cleaned up.
@SuppressWarnings("deprecation")
@RestrictTo(LIBRARY)
public class OnClickListenerWrapperImpl implements OnClickListenerWrapper {

    @Keep
    private final boolean mIsParkedOnly;
    @Keep
    private final IOnClickListener mListener;

    /**
     * Whether the click listener is for parked-only scenarios.
     */
    @Override
    public boolean isParkedOnly() {
        return mIsParkedOnly;
    }

    @Override
    public void onClick(@NonNull OnDoneCallback callback) {
        try {
            mListener.onClick(RemoteUtils.createOnDoneCallbackStub(callback));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    // This listener relates to UI event and is expected to be triggered on the main thread.
    @SuppressLint("ExecutorRegistration")
    static OnClickListenerWrapper create(@NonNull OnClickListener listener) {
        return new OnClickListenerWrapperImpl(
                listener,
                listener instanceof ParkedOnlyOnClickListener);
    }

    private OnClickListenerWrapperImpl(@NonNull OnClickListener listener,
            boolean isParkedOnly) {
        this.mListener = new OnClickListenerStub(listener);
        this.mIsParkedOnly = isParkedOnly;
    }

    /** For serialization. */
    private OnClickListenerWrapperImpl() {
        mListener = null;
        mIsParkedOnly = false;
    }

    @Keep // We need to keep these stub for Bundler serialization logic.
    private static class OnClickListenerStub extends IOnClickListener.Stub {
        private final OnClickListener mOnClickListener;

        OnClickListenerStub(OnClickListener onClickListener) {
            this.mOnClickListener = onClickListener;
        }

        @Override
        public void onClick(IOnDoneCallback callback) {
            RemoteUtils.dispatchHostCall(mOnClickListener::onClick, callback, "onClick");
        }
    }
}
