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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.media.AudioManager;

import androidx.annotation.IntDef;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 * Handles requests to adjust or set the volume on a session. This is also used
 * to push volume updates back to the session. The provider must call
 * {@link #setCurrentVolume(int)} each time the volume being provided changes.
 * <p>
 * You can set a volume provider on a session by calling
 * {@link MediaSession2#updatePlayer}.
 */
// New version of VolumeProvider with following changes
//   - Don't implement Parcelable for updatable support.
@RestrictTo(LIBRARY_GROUP)
public abstract class VolumeProvider2 {
    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({VOLUME_CONTROL_FIXED, VOLUME_CONTROL_RELATIVE, VOLUME_CONTROL_ABSOLUTE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ControlType {}

    /**
     * The volume is fixed and can not be modified. Requests to change volume
     * should be ignored.
     */
    public static final int VOLUME_CONTROL_FIXED = 0;

    /**
     * The volume control uses relative adjustment via
     * {@link #onAdjustVolume(int)}. Attempts to set the volume to a specific
     * value should be ignored.
     */
    public static final int VOLUME_CONTROL_RELATIVE = 1;

    /**
     * The volume control uses an absolute value. It may be adjusted using
     * {@link #onAdjustVolume(int)} or set directly using
     * {@link #onSetVolumeTo(int)}.
     */
    public static final int VOLUME_CONTROL_ABSOLUTE = 2;

    private final int mControlType;
    private final int mMaxVolume;
    private int mCurrentVolume;
    private Callback mCallback;

    /**
     * Create a new volume provider for handling volume events. You must specify
     * the type of volume control, the maximum volume that can be used, and the
     * current volume on the output.
     *
     * @param controlType The method for controlling volume that is used by this provider.
     * @param maxVolume The maximum allowed volume.
     * @param currentVolume The current volume on the output.
     */
    public VolumeProvider2(@ControlType int controlType, int maxVolume, int currentVolume) {
        if (controlType != VOLUME_CONTROL_FIXED && controlType != VOLUME_CONTROL_RELATIVE
                && controlType != VOLUME_CONTROL_ABSOLUTE) {
            throw new IllegalArgumentException("wrong controlType " + controlType);
        }
        if (maxVolume < 0 || currentVolume < 0) {
            throw new IllegalArgumentException("volume shouldn't be negative"
                    + ", maxVolume=" + maxVolume + ", currentVolume=" + currentVolume);
        }
        if (currentVolume > maxVolume) {
            throw new IllegalArgumentException("currentVolume shouldn't be greater than maxVolume"
                    + ", maxVolume=" + maxVolume + ", currentVolume=" + currentVolume);
        }
        mControlType = controlType;
        mMaxVolume = maxVolume;
        mCurrentVolume = currentVolume;
    }

    /**
     * Get the volume control type that this volume provider uses.
     *
     * @return The volume control type for this volume provider
     */
    @ControlType
    public final int getControlType() {
        return mControlType;
    }

    /**
     * Get the maximum volume this provider allows.
     *
     * @return The max allowed volume.
     */
    public final int getMaxVolume() {
        return mMaxVolume;
    }

    /**
     * Gets the current volume. This will be the last value set by
     * {@link #setCurrentVolume(int)}.
     *
     * @return The current volume.
     */
    public final int getCurrentVolume() {
        return mCurrentVolume;
    }

    /**
     * Notify the system that the current volume has been changed. This must be
     * called every time the volume changes to ensure it is displayed properly.
     *
     * @param currentVolume The current volume on the output.
     */
    public final void setCurrentVolume(int currentVolume) {
        if (currentVolume < 0) {
            throw new IllegalArgumentException("currentVolume shouldn't be negative"
                    + ", currentVolume=" + currentVolume);
        }
        mCurrentVolume = currentVolume;
        if (mCallback != null) {
            mCallback.onVolumeChanged(this);
        }
    }

    /**
     * Override to handle requests to set the volume of the current output.
     * After the volume has been modified {@link #setCurrentVolume} must be
     * called to notify the system.
     *
     * @param volume The volume to set the output to.
     */
    public void onSetVolumeTo(int volume) { }

    /**
     * Override to handle requests to adjust the volume of the current output.
     * Direction will be one of {@link AudioManager#ADJUST_LOWER},
     * {@link AudioManager#ADJUST_RAISE}, {@link AudioManager#ADJUST_SAME}.
     * After the volume has been modified {@link #setCurrentVolume} must be
     * called to notify the system.
     *
     * @param direction The direction to change the volume in.
     */
    public void onAdjustVolume(int direction) { }

    /**
     * Sets a callback to receive volume changes.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Listens for changes to the volume.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public abstract static class Callback {
        /**
         * Called when {@link #setCurrentVolume(int)} is called.
         */
        public abstract void onVolumeChanged(VolumeProvider2 volumeProvider);
    }
}
