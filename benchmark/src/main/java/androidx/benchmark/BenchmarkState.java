/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.benchmark;

import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Provides a benchmark framework.
 *
 * Example usage:
 * // Executes the code while keepRunning returning true.
 *
 * public void sampleMethod() {
 *     BenchmarkState state = new BenchmarkState();
 *
 *     int[] src = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
 *     while (state.keepRunning()) {
 *         int[] dest = new int[src.length];
 *         System.arraycopy(src, 0, dest, 0, src.length);
 *     }
 *     System.out.println(state.summaryLine());
 * }
 */
public final class BenchmarkState {
    private static final String TAG = "Benchmark";
    private static final String CSV_TAG = "BenchmarkCsv";
    private static final String STUDIO_OUTPUT_KEY_PREFIX = "android.studio.display.";
    private static final String STUDIO_OUTPUT_KEY_ID = "benchmark";

    private static final boolean ENABLE_PROFILING = false;

    private static final int NOT_STARTED = 0;  // The benchmark has not started yet.
    private static final int WARMUP = 1; // The benchmark is warming up.
    private static final int RUNNING = 2;  // The benchmark is running.
    private static final int FINISHED = 3;  // The benchmark has stopped.

    // values determined empirically
    private static final long TARGET_TEST_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final int MAX_TEST_ITERATIONS = 1000000;
    private static final int MIN_TEST_ITERATIONS = 10;
    private static final int REPEAT_COUNT = 5;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("Benchmark");
        for (int i = 0; i < REPEAT_COUNT; i++) {
            sb.append(", Result ").append(i);
        }

        Log.i(CSV_TAG, sb.toString());
    }

    private int mState = NOT_STARTED;  // Current benchmark state.

    private WarmupManager mWarmupManager = new WarmupManager();

    private long mStartTimeNs = 0; // System.nanoTime() at start of last warmup iter / test repeat.

    private boolean mPaused;
    private long mPausedTimeNs = 0; // The System.nanoTime() when the pauseTiming() is called.
    private long mPausedDurationNs = 0;  // The duration of paused state in nano sec.

    private int mIteration = 0;
    private int mMaxIterations = 0;

    private int mRepeatCount = 0;

    // Statistics. These values will be filled when the benchmark has finished.
    // The computation needs double precision, but long int is fine for final reporting.
    private Stats mStats;

    // Individual duration in nano seconds.
    private ArrayList<Long> mResults = new ArrayList<>();

    /**
     * Stops the benchmark timer.
     * <p>
     * This method can be called only when the timer is running.
     */
    public void pauseTiming() {
        if (mPaused) {
            throw new IllegalStateException(
                    "Unable to pause the benchmark. The benchmark has already paused.");
        }
        mPausedTimeNs = System.nanoTime();
        mPaused = true;
    }

    /**
     * Starts the benchmark timer.
     * <p>
     * This method can be called only when the timer is stopped.
     */
    public void resumeTiming() {
        if (!mPaused) {
            throw new IllegalStateException(
                    "Unable to resume the benchmark. The benchmark is already running.");
        }
        mPausedDurationNs += System.nanoTime() - mPausedTimeNs;
        mPausedTimeNs = 0;
        mPaused = false;
    }

    private void beginWarmup() {
        mStartTimeNs = System.nanoTime();
        mIteration = 0;
        mState = WARMUP;
    }

    private void beginBenchmark() {
        if (ENABLE_PROFILING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // TODO: support data dir for old platforms
            File f = new File(
                    InstrumentationRegistry.getInstrumentation().getContext().getDataDir(),
                    "benchprof");
            Log.d(TAG, "Tracing to: " + f.getAbsolutePath());
            Debug.startMethodTracingSampling(f.getAbsolutePath(), 16 * 1024 * 1024, 100);
        }
        final int idealIterations =
                (int) (TARGET_TEST_DURATION_NS / mWarmupManager.getEstimatedIterationTime());
        mMaxIterations = Math.min(MAX_TEST_ITERATIONS,
                Math.max(idealIterations, MIN_TEST_ITERATIONS));
        mPausedDurationNs = 0;
        mIteration = 0;
        mRepeatCount = 0;
        mState = RUNNING;
        mStartTimeNs = System.nanoTime();
    }

    private boolean startNextTestRun() {
        final long currentTime = System.nanoTime();
        mResults.add((currentTime - mStartTimeNs - mPausedDurationNs) / mMaxIterations);
        mRepeatCount++;
        if (mRepeatCount >= REPEAT_COUNT) {
            if (ENABLE_PROFILING) {
                Debug.stopMethodTracing();
            }
            mStats = new Stats(mResults);
            mState = FINISHED;
            return false;
        }
        mPausedDurationNs = 0;
        mIteration = 0;
        mStartTimeNs = System.nanoTime();
        return true;
    }

    /**
     * Judges whether the benchmark needs more samples.
     *
     * For the usage, see class comment.
     */
    public boolean keepRunning() {
        switch (mState) {
            case NOT_STARTED:
                beginWarmup();
                return true;
            case WARMUP:
                mIteration++;
                // Only check nanoTime on every iteration in WARMUP since we
                // don't yet have a target iteration count.
                final long time = System.nanoTime();
                final long lastDuration = time - mStartTimeNs;
                mStartTimeNs = time;
                if (mWarmupManager.onNextIteration(lastDuration)) {
                    beginBenchmark();
                }
                return true;
            case RUNNING:
                mIteration++;
                if (mIteration >= mMaxIterations) {
                    return startNextTestRun();
                }
                if (mPaused) {
                    throw new IllegalStateException("Benchmark step finished with paused state. "
                            + "Resume the benchmark before finishing each step.");
                }
                return true;
            case FINISHED:
                throw new IllegalStateException("The benchmark has finished.");
            default:
                throw new IllegalStateException("The benchmark is in unknown state.");
        }
    }

    /**
     * Get the end of run benchmark statistics.
     * <p>
     * This method may only be called keepRunning() returns {@code false}.
     *
     * @return Stats from run.
     */
    @NonNull
    public Stats getStats() {
        if (mState != FINISHED) {
            throw new IllegalStateException("The benchmark hasn't finished");
        }
        return mStats;
    }

    private long mean() {
        return (long) getStats().getMean();
    }

    private long median() {
        return getStats().getMedian();
    }

    private long min() {
        return getStats().getMin();
    }

    private long standardDeviation() {
        return (long) getStats().getStandardDeviation();
    }

    private long count() {
        return mMaxIterations;
    }

    private String summaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary: ");
        sb.append("median=").append(median()).append("ns, ");
        sb.append("mean=").append(mean()).append("ns, ");
        sb.append("min=").append(min()).append("ns, ");
        sb.append("stddev=").append(standardDeviation()).append(", ");
        sb.append("count=").append(count()).append(", ");
        // print out the first few iterations' number for double checking.
        int sampleNumber = Math.min(mResults.size(), 16);
        for (int i = 0; i < sampleNumber; i++) {
            sb.append("No ").append(i).append(" result is ").append(mResults.get(i)).append(", ");
        }
        return sb.toString();
    }

    @NonNull
    private String ideSummaryLineWrapped(@NonNull String key) {
        StringBuilder result = null;

        String warningString = WarningState.acquireWarningStringForLogging();
        if (warningString != null) {
            for (String s : warningString.split("\n")) {
                if (result == null) {
                    if (s.isEmpty()) {
                        continue;
                    }
                    result = new StringBuilder(s).append("\n");
                } else {
                    result.append(STUDIO_OUTPUT_KEY_ID).append(": ").append(s).append("\n");
                }
            }
            if (result != null) {
                result.append(STUDIO_OUTPUT_KEY_ID).append(": ").append(ideSummaryLine(key));
                return result.toString();
            }
        }

        return ideSummaryLine(key);
    }

    @NonNull
    String ideSummaryLine(@NonNull String key) {
        // NOTE: this summary line will use default locale to determine separators. As
        // this line is only meant for human eyes, we don't worry about consistency here.
        return String.format(
                // 13 is used for alignment here, because it's enough that 9.99sec will still
                // align with any other output, without moving data too far to the right
                "%13s ns %s",
                NumberFormat.getNumberInstance().format(min()),
                key);
    }

    private String csvLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mResults.size(); i++) {
            sb.append(", ").append(mResults.get(i));
        }
        return sb.toString();
    }

    /**
     * Acquires a status report bundle
     *
     * @param key Run identifier, prepended to bundle properties.
     */
    Bundle getFullStatusReport(@NonNull String key) {
        key = WarningState.WARNING_PREFIX + key;
        Log.i(TAG, key + summaryLine());
        Log.i(CSV_TAG, key + csvLine());
        Bundle status = new Bundle();
        status.putLong(key + "_median", median());
        status.putLong(key + "_mean", mean());
        status.putLong(key + "_min", min());
        status.putLong(key + "_standardDeviation", standardDeviation());
        status.putLong(key + "_count", count());
        status.putString(STUDIO_OUTPUT_KEY_PREFIX + STUDIO_OUTPUT_KEY_ID,
                ideSummaryLineWrapped(key));
        return status;
    }
}
