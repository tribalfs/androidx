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

package androidx.wear.ongoing;

import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.versionedparcelable.NonParcelField;
import androidx.versionedparcelable.ParcelField;
import androidx.versionedparcelable.VersionedParcelize;

import java.util.Objects;

/**
 * An Ongoing activity status (or part of it) representing a timer or stopwatch.
 *
 *  Available since wear-ongoing:1.0.0
 */
@VersionedParcelize
public class TimerStatusPart extends StatusPart {
    @ParcelField(value = 1, defaultValue = "0")
    long mTimeZeroMillis;

    @ParcelField(value = 2, defaultValue = "false")
    boolean mCountDown = false;

    @ParcelField(value = 3, defaultValue = "-1")
    long mPausedAtMillis = LONG_DEFAULT;

    @ParcelField(value = 4, defaultValue = "-1")
    long mTotalDurationMillis = LONG_DEFAULT;

    @NonParcelField
    private final StringBuilder mStringBuilder = new StringBuilder(8);

    private static final String NEGATIVE_DURATION_PREFIX = "-";

    // Required by VersionedParcelable
    TimerStatusPart() {
    }

    /**
     * Create a Status representing a timer or stopwatch.
     *
     * @param timeZeroMillis      timestamp of the time at which this Timer should display 0,
     *                            will be in the
     *                            past for a stopwatch and usually in the future for timers.
     * @param countDown           indicates if this is a stopwatch (when {@code false} or timer
     *                            (when {@code true}).
     * @param pausedAtMillis      timestamp of the time when this timer was paused. Or
     *                            {@code -1L} if this
     *                            timer is running.
     * @param totalDurationMillis total duration of this timer/stopwatch, useful to display as a
     *                            progress bar or similar.
     */
    public TimerStatusPart(long timeZeroMillis, boolean countDown, long pausedAtMillis,
            long totalDurationMillis) {
        this.mTimeZeroMillis = timeZeroMillis;
        this.mCountDown = countDown;
        this.mPausedAtMillis = pausedAtMillis;
        this.mTotalDurationMillis = totalDurationMillis;
    }

    /**
     * Create a Status representing a timer or stopwatch.
     *
     * @param timeZeroMillis      timestamp of the time at which this Timer should display 0,
     *                            will be in the
     *                            past for a stopwatch and usually in the future for timers.
     * @param countDown           indicates if this is a stopwatch (when {@code false}) or timer
     *                            (when {@code true}).
     * @param pausedAtMillis      timestamp of the time when this timer was paused. Or
     *                            {@code -1L} if this timer is running.
     */
    public TimerStatusPart(long timeZeroMillis, boolean countDown, long pausedAtMillis) {
        this(timeZeroMillis, countDown, pausedAtMillis, LONG_DEFAULT);
    }

    /**
     * Create a Status representing a timer or stopwatch.
     *
     * @param timeZeroMillis      timestamp of the time at which this Timer should display 0,
     *                            will be in the
     *                            past for a stopwatch and usually in the future for timers.
     * @param countDown           indicates if this is a stopwatch (when {@code false}) or timer
     *                            (when {@code true}).
     */
    public TimerStatusPart(long timeZeroMillis, boolean countDown) {
        this(timeZeroMillis, countDown, LONG_DEFAULT);
    }

    /**
     * Create a Status representing stopwatch.
     *
     * @param timeZeroMillis      timestamp of the time at which this Stopwatch started.
     */
    public TimerStatusPart(long timeZeroMillis) {
        this(timeZeroMillis, false);
    }

    /**
     * See {@link TimeDependentText#getText(Context, long)}]
     */
    @NonNull
    @Override
    public CharSequence getText(@NonNull Context context, long timeNowMillis) {
        long timeMillis = isPaused() ? mPausedAtMillis : timeNowMillis;
        long milliSeconds = timeMillis - mTimeZeroMillis;
        long seconds = milliSeconds >= 0 ? milliSeconds / 1000
                // Always round down (instead of the default round to 0) so all values are displayed
                // for 1 second.
                : (milliSeconds - 999) / 1000;

        if (mCountDown) {
            seconds = -seconds;
        }

        String prefix = "";
        if (seconds < 0) {
            seconds = -seconds;
            prefix = NEGATIVE_DURATION_PREFIX;
        }

        return prefix + DateUtils.formatElapsedTime(mStringBuilder, seconds);
    }

    /**
     * See {@link TimeDependentText#getNextChangeTimeMillis(long)}
     */
    @Override
    public long getNextChangeTimeMillis(long fromTimeMillis) {
        return isPaused() ? Long.MAX_VALUE :
                // We always want to return a value:
                //    * Strictly greater than fromTimeMillis.
                //    * Has the same millis as timeZero.
                //    * It's as small as possible.
                fromTimeMillis + ((mTimeZeroMillis - fromTimeMillis) % 1000 + 1999) % 1000 + 1;
    }

    /**
     * @return the time at which this Timer will display 0, will be in the past for a stopwatch
     * and usually in the future for timers.
     */
    public long getTimeZeroMillis() {
        return mTimeZeroMillis;
    }

    /**
     * @return {@code false} if this is a stopwatch or {@code true} if this is a timer.
     */
    public boolean isCountDown() {
        return mCountDown;
    }

    /**
     * Determines if this timer is paused. i.e. the display representation will not change over
     * time.
     *
     * @return {@code true} if this timer is paused, {@code false} if it's running.
     */
    public boolean isPaused() {
        return mPausedAtMillis >= 0L;
    }

    /**
     * @return the timestamp of the time when this timer was paused. Use
     * {@link TimerStatusPart#isPaused()} to determine if this timer is paused or not.
     */
    public long getPausedAtMillis() {
        return mPausedAtMillis;
    }

    /**
     * Determines if this timer has a total duration set.
     *
     * @return {@code true} if this the total duration was set, {@code false} if not.
     */
    public boolean hasTotalDuration() {
        return mTotalDurationMillis >= 0L;
    }

    /**
     * @return the total duration of this timer/stopwatch, if set. Use
     * {@link TimerStatusPart#hasTotalDuration()} to determine if this timer has a
     * duration set.
     */
    public long getTotalDurationMillis() {
        return mTotalDurationMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TimerStatusPart)) return false;
        TimerStatusPart that = (TimerStatusPart) o;
        return mTimeZeroMillis == that.mTimeZeroMillis
                && mCountDown == that.mCountDown
                && mPausedAtMillis == that.mPausedAtMillis
                && mTotalDurationMillis == that.mTotalDurationMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTimeZeroMillis, mCountDown, mPausedAtMillis, mTotalDurationMillis);
    }

    // Invalid value to use for paused_at and duration, as suggested by api guidelines 5.15
    static final long LONG_DEFAULT = -1L;
}
