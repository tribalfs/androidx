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

package androidx.wear.ongoingactivity;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * {@link OngoingActivityStatus} representing a timer or stopwatch.
 */
public class TimerOngoingActivityStatus extends OngoingActivityStatus {
    private long mTimeZeroMillis;
    private boolean mCountDown = false;
    private long mPausedAtMillis = LONG_DEFAULT;
    private long mTotalDurationMillis = LONG_DEFAULT;

    private final StringBuilder mStringBuilder = new StringBuilder(8);
    private static final String NEGATIVE_DURATION_PREFIX = "-";

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
    public TimerOngoingActivityStatus(long timeZeroMillis, boolean countDown, long pausedAtMillis,
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
    public TimerOngoingActivityStatus(long timeZeroMillis, boolean countDown, long pausedAtMillis) {
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
    public TimerOngoingActivityStatus(long timeZeroMillis, boolean countDown) {
        this(timeZeroMillis, countDown, LONG_DEFAULT);
    }

    /**
     * Create a Status representing stopwatch.
     *
     * @param timeZeroMillis      timestamp of the time at which this Stopwatch started.
     */
    public TimerOngoingActivityStatus(long timeZeroMillis) {
        this(timeZeroMillis, false);
    }

    /**
     * See {@link OngoingActivityStatus#getText(Context, long)}]
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
     * See {@link OngoingActivityStatus#getNextChangeTimeMillis(long)}
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

    @Override
    void extend(Bundle bundle) {
        bundle.putBoolean(KEY_USE_CHRONOMETER, true);
        bundle.putLong(KEY_TIME_ZERO, mTimeZeroMillis);
        bundle.putBoolean(KEY_COUNT_DOWN, mCountDown);
        if (mTotalDurationMillis != LONG_DEFAULT) {
            bundle.putLong(KEY_TOTAL_DURATION, mTotalDurationMillis);
        }
        if (mPausedAtMillis != LONG_DEFAULT) {
            bundle.putLong(KEY_PAUSED_AT, mPausedAtMillis);
        }
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
     * {@link TimerOngoingActivityStatus#isPaused()} to determine if this timer is paused or not.
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
     * {@link TimerOngoingActivityStatus#hasTotalDuration()} to determine if this timer has a
     * duration set.
     */
    public long getTotalDurationMillis() {
        return mTotalDurationMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimerOngoingActivityStatus)) return false;
        TimerOngoingActivityStatus that = (TimerOngoingActivityStatus) o;
        return mTimeZeroMillis == that.mTimeZeroMillis
                && mCountDown == that.mCountDown
                && mPausedAtMillis == that.mPausedAtMillis
                && mTotalDurationMillis == that.mTotalDurationMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTimeZeroMillis, mCountDown, mPausedAtMillis, mTotalDurationMillis);
    }
}
