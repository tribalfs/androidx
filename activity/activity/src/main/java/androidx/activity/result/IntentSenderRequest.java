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

package androidx.activity.result;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A request for a
 * {@link androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult}
 * Activity Contract.
 */
@SuppressLint("BanParcelableUsage")
public final class IntentSenderRequest implements Parcelable {
    @NonNull
    private final IntentSender mIntentSender;
    @Nullable
    private final Intent mFillInIntent;
    private final int mFlagsMask;
    private final int mFlagsValues;

    IntentSenderRequest(@NonNull IntentSender intentSender, @Nullable Intent intent, int flagsMask,
            int flagsValues) {
        mIntentSender = intentSender;
        mFillInIntent = intent;
        mFlagsMask = flagsMask;
        mFlagsValues = flagsValues;
    }

    /**
     * Get the intentSender from this IntentSenderRequest.
     *
     * @return the IntentSender to launch.
     */
    @NonNull
    public IntentSender getIntentSender() {
        return mIntentSender;
    }

    /**
     * Get the intent from this IntentSender request.  If non-null, this will be provided as the
     * intent parameter to IntentSender#sendIntent.
     *
     * @return the fill in intent.
     */
    @Nullable
    public Intent getFillInIntent() {
        return mFillInIntent;
    }

    /**
     * Get the flag mask from this IntentSender request.
     *
     * @return intent flags in the original IntentSender that you would like to change.
     */
    public int getFlagsMask() {
        return mFlagsMask;
    }

    /**
     * Get the flag values from this IntentSender request.
     *
     * @return desired values for any bits set in flagsMask
     */
    public int getFlagsValues() {
        return mFlagsValues;
    }

    @SuppressWarnings("ConstantConditions")
    IntentSenderRequest(@NonNull Parcel in) {
        mIntentSender = in.readParcelable(IntentSender.class.getClassLoader());
        mFillInIntent = in.readParcelable(Intent.class.getClassLoader());
        mFlagsMask = in.readInt();
        mFlagsValues = in.readInt();
    }

    @NonNull
    public static final Creator<IntentSenderRequest> CREATOR = new Creator<IntentSenderRequest>() {
        @Override
        public IntentSenderRequest createFromParcel(Parcel in) {
            return new IntentSenderRequest(in);
        }

        @Override
        public IntentSenderRequest[] newArray(int size) {
            return new IntentSenderRequest[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mIntentSender, flags);
        dest.writeParcelable(mFillInIntent, flags);
        dest.writeInt(mFlagsMask);
        dest.writeInt(mFlagsValues);
    }

    /**
     * A builder for constructing {@link IntentSenderRequest} instances.
     */
    public static final class Builder {
        private IntentSender mIntentSender;
        private Intent mFillInIntent;
        private int mFlagsMask;
        private int mFlagsValues;

        /**
         * Constructor that takes an {@link IntentSender} and sets it for the builder.
         *
         * @param intentSender IntentSender to go in the IntentSenderRequest.
         */
        public Builder(@NonNull IntentSender intentSender) {
            mIntentSender = intentSender;
        }

        /**
         * Convenience constructor that takes an {@link PendingIntent} and uses
         * its {@link IntentSender}.
         *
         * @param pendingIntent the pendingIntent containing with the intentSender to go in the
         *                      IntentSenderRequest.
         */
        public Builder(@NonNull PendingIntent pendingIntent) {
            this(pendingIntent.getIntentSender());
        }

        /**
         * Set the intent for the {@link IntentSenderRequest}.
         *
         * @param fillInIntent intent to go in the IntentSenderRequest. If non-null, this
         *                     will be provided as the intent parameter to IntentSender#sendIntent.
         * @return This builder.
         */
        @NonNull
        public Builder setFillInIntent(@Nullable Intent fillInIntent) {
            mFillInIntent = fillInIntent;
            return this;
        }

        /**
         * Set the flag mask and flag values for the {@link IntentSenderRequest}.
         *
         * @param mask mask to go in the IntentSenderRequest. Intent flags in the original
         *             IntentSender that you would like to change.
         * @param values flagValues to go in the IntentSenderRequest. Desired values for any bits
         *             set in flagsMask
         *
         * @return This builder.
         */
        @NonNull
        public Builder setFlags(int mask, int values) {
            mFlagsMask = mask;
            mFlagsValues = values;
            return this;
        }

        /**
         * Build the IntentSenderRequest specified by this builder.
         *
         * @return the newly constructed IntentSenderRequest.
         */
        @NonNull
        public IntentSenderRequest build() {
            return new IntentSenderRequest(mIntentSender, mFillInIntent, mFlagsMask, mFlagsValues);
        }
    }
}
