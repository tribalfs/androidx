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

package androidx.loader.content;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.os.OperationCanceledException;
import androidx.core.util.TimeUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Static library support version of the framework's {@link android.content.AsyncTaskLoader}.
 * Used to write apps that run on platforms prior to Android 3.0.  When running
 * on Android 3.0 or above, this implementation is still used; it does not try
 * to switch to the framework's implementation.  See the framework SDK
 * documentation for a class overview.
 */
public abstract class AsyncTaskLoader<D> extends Loader<D> {
    static final String TAG = "AsyncTaskLoader";
    static final boolean DEBUG = false;

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = 128;
    private static final int KEEP_ALIVE = 1;

    static final class CountingThreadFactory extends AtomicInteger implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, TAG + " #" + incrementAndGet());
        }
    }

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    private static final Executor THREAD_POOL_EXECUTOR =
            new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                    TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10),
                    new CountingThreadFactory());

    final class LoadTask extends ModernAsyncTask<D> implements Runnable {
        private final CountDownLatch mDone = new CountDownLatch(1);

        // Set to true to indicate that the task has been posted to a handler for
        // execution at a later time.  Used to throttle updates.
        boolean waiting;

        /* Runs on a worker thread */
        @Override
        protected D doInBackground() {
            if (DEBUG) Log.v(TAG, this + " >>> doInBackground");
            try {
                D data = AsyncTaskLoader.this.onLoadInBackground();
                if (DEBUG) Log.v(TAG, this + "  <<< doInBackground");
                return data;
            } catch (OperationCanceledException ex) {
                if (!isCancelled()) {
                    // onLoadInBackground threw a canceled exception spuriously.
                    // This is problematic because it means that the LoaderManager did not
                    // cancel the Loader itself and still expects to receive a result.
                    // Additionally, the Loader's own state will not have been updated to
                    // reflect the fact that the task was being canceled.
                    // So we treat this case as an unhandled exception.
                    throw ex;
                }
                if (DEBUG) Log.v(TAG, this + "  <<< doInBackground (was canceled)", ex);
                return null;
            }
        }

        /* Runs on the UI thread */
        @Override
        protected void onPostExecute(D data) {
            if (DEBUG) Log.v(TAG, this + " onPostExecute");
            try {
                AsyncTaskLoader.this.dispatchOnLoadComplete(this, data);
            } finally {
                mDone.countDown();
            }
        }

        /* Runs on the UI thread */
        @Override
        protected void onCancelled(D data) {
            if (DEBUG) Log.v(TAG, this + " onCancelled");
            try {
                AsyncTaskLoader.this.dispatchOnCancelled(this, data);
            } finally {
                mDone.countDown();
            }
        }

        /* Runs on the UI thread, when the waiting task is posted to a handler.
         * This method is only executed when task execution was deferred (waiting was true). */
        @Override
        public void run() {
            waiting = false;
            AsyncTaskLoader.this.executePendingTask();
        }

        /* Used for testing purposes to wait for the task to complete. */
        public void waitForLoader() {
            try {
                mDone.await();
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    private final Executor mExecutor;

    volatile LoadTask mTask;
    volatile LoadTask mCancellingTask;

    long mUpdateThrottle;
    long mLastLoadCompleteTime = -10000;
    Handler mHandler;

    public AsyncTaskLoader(@NonNull Context context) {
        this(context, THREAD_POOL_EXECUTOR);
    }

    private AsyncTaskLoader(@NonNull Context context, @NonNull Executor executor) {
        super(context);
        mExecutor = executor;
    }

    /**
     * Set amount to throttle updates by.  This is the minimum time from
     * when the last {@link #loadInBackground()} call has completed until
     * a new load is scheduled.
     *
     * @param delayMS Amount of delay, in milliseconds.
     */
    public void setUpdateThrottle(long delayMS) {
        mUpdateThrottle = delayMS;
        if (delayMS != 0) {
            mHandler = new Handler();
        }
    }

    @Override
    protected void onForceLoad() {
        super.onForceLoad();
        cancelLoad();
        mTask = new LoadTask();
        if (DEBUG) Log.v(TAG, "Preparing load: mTask=" + mTask);
        executePendingTask();
    }

    @Override
    protected boolean onCancelLoad() {
        if (DEBUG) Log.v(TAG, "onCancelLoad: mTask=" + mTask);
        if (mTask != null) {
            if (!mStarted) {
                mContentChanged = true;
            }
            if (mCancellingTask != null) {
                // There was a pending task already waiting for a previous
                // one being canceled; just drop it.
                if (DEBUG) Log.v(TAG,
                        "cancelLoad: still waiting for cancelled task; dropping next");
                if (mTask.waiting) {
                    mTask.waiting = false;
                    mHandler.removeCallbacks(mTask);
                }
                mTask = null;
                return false;
            } else if (mTask.waiting) {
                // There is a task, but it is waiting for the time it should
                // execute.  We can just toss it.
                if (DEBUG) Log.v(TAG, "cancelLoad: task is waiting, dropping it");
                mTask.waiting = false;
                mHandler.removeCallbacks(mTask);
                mTask = null;
                return false;
            } else {
                boolean cancelled = mTask.cancel(false);
                if (DEBUG) Log.v(TAG, "cancelLoad: cancelled=" + cancelled);
                if (cancelled) {
                    mCancellingTask = mTask;
                    cancelLoadInBackground();
                }
                mTask = null;
                return cancelled;
            }
        }
        return false;
    }

    /**
     * Called if the task was canceled before it was completed.  Gives the class a chance
     * to clean up post-cancellation and to properly dispose of the result.
     *
     * @param data The value that was returned by {@link #loadInBackground}, or null
     * if the task threw {@link OperationCanceledException}.
     */
    public void onCanceled(@Nullable D data) {
    }

    void executePendingTask() {
        if (mCancellingTask == null && mTask != null) {
            if (mTask.waiting) {
                mTask.waiting = false;
                mHandler.removeCallbacks(mTask);
            }
            if (mUpdateThrottle > 0) {
                long now = SystemClock.uptimeMillis();
                if (now < (mLastLoadCompleteTime+mUpdateThrottle)) {
                    // Not yet time to do another load.
                    if (DEBUG) Log.v(TAG, "Waiting until "
                            + (mLastLoadCompleteTime+mUpdateThrottle)
                            + " to execute: " + mTask);
                    mTask.waiting = true;
                    mHandler.postAtTime(mTask, mLastLoadCompleteTime+mUpdateThrottle);
                    return;
                }
            }
            if (DEBUG) Log.v(TAG, "Executing: " + mTask);
            mTask.executeOnExecutor(mExecutor);
        }
    }

    void dispatchOnCancelled(LoadTask task, D data) {
        onCanceled(data);
        if (mCancellingTask == task) {
            if (DEBUG) Log.v(TAG, "Cancelled task is now canceled!");
            rollbackContentChanged();
            mLastLoadCompleteTime = SystemClock.uptimeMillis();
            mCancellingTask = null;
            if (DEBUG) Log.v(TAG, "Delivering cancellation");
            deliverCancellation();
            executePendingTask();
        }
    }

    void dispatchOnLoadComplete(LoadTask task, D data) {
        if (mTask != task) {
            if (DEBUG) Log.v(TAG, "Load complete of old task, trying to cancel");
            dispatchOnCancelled(task, data);
        } else {
            if (isAbandoned()) {
                // This cursor has been abandoned; just cancel the new data.
                onCanceled(data);
            } else {
                commitContentChanged();
                mLastLoadCompleteTime = SystemClock.uptimeMillis();
                mTask = null;
                if (DEBUG) Log.v(TAG, "Delivering result");
                deliverResult(data);
            }
        }
    }

    /**
     * Called on a worker thread to perform the actual load and to return
     * the result of the load operation.
     *
     * Implementations should not deliver the result directly, but should return them
     * from this method, which will eventually end up calling {@link #deliverResult} on
     * the UI thread.  If implementations need to process the results on the UI thread
     * they may override {@link #deliverResult} and do so there.
     *
     * To support cancellation, this method should periodically check the value of
     * {@link #isLoadInBackgroundCanceled} and terminate when it returns true.
     * Subclasses may also override {@link #cancelLoadInBackground} to interrupt the load
     * directly instead of polling {@link #isLoadInBackgroundCanceled}.
     *
     * When the load is canceled, this method may either return normally or throw
     * {@link OperationCanceledException}.  In either case, the {@link Loader} will
     * call {@link #onCanceled} to perform post-cancellation cleanup and to dispose of the
     * result object, if any.
     *
     * @return The result of the load operation.
     *
     * @throws OperationCanceledException if the load is canceled during execution.
     *
     * @see #isLoadInBackgroundCanceled
     * @see #cancelLoadInBackground
     * @see #onCanceled
     */
    @Nullable
    public abstract D loadInBackground();

    /**
     * Calls {@link #loadInBackground()}.
     *
     * This method is reserved for use by the loader framework.
     * Subclasses should override {@link #loadInBackground} instead of this method.
     *
     * @return The result of the load operation.
     *
     * @throws OperationCanceledException if the load is canceled during execution.
     *
     * @see #loadInBackground
     */
    @Nullable
    protected D onLoadInBackground() {
        return loadInBackground();
    }

    /**
     * Called on the main thread to abort a load in progress.
     *
     * Override this method to abort the current invocation of {@link #loadInBackground}
     * that is running in the background on a worker thread.
     *
     * This method should do nothing if {@link #loadInBackground} has not started
     * running or if it has already finished.
     *
     * @see #loadInBackground
     */
    public void cancelLoadInBackground() {
    }

    /**
     * Returns true if the current invocation of {@link #loadInBackground} is being canceled.
     *
     * @return True if the current invocation of {@link #loadInBackground} is being canceled.
     *
     * @see #loadInBackground
     */
    public boolean isLoadInBackgroundCanceled() {
        return mCancellingTask != null;
    }

    /**
     * Locks the current thread until the loader completes the current load
     * operation. Returns immediately if there is no load operation running.
     * Should not be called from the UI thread: calling it from the UI
     * thread would cause a deadlock.
     * <p>
     * Use for testing only.  <b>Never</b> call this from a UI thread.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void waitForLoader() {
        LoadTask task = mTask;
        if (task != null) {
            task.waitForLoader();
        }
    }

    @Override
    @Deprecated
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (mTask != null) {
            writer.print(prefix); writer.print("mTask="); writer.print(mTask);
                    writer.print(" waiting="); writer.println(mTask.waiting);
        }
        if (mCancellingTask != null) {
            writer.print(prefix); writer.print("mCancellingTask="); writer.print(mCancellingTask);
                    writer.print(" waiting="); writer.println(mCancellingTask.waiting);
        }
        if (mUpdateThrottle != 0) {
            writer.print(prefix); writer.print("mUpdateThrottle=");
                    TimeUtils.formatDuration(mUpdateThrottle, writer);
                    writer.print(" mLastLoadCompleteTime=");
                    TimeUtils.formatDuration(mLastLoadCompleteTime,
                            SystemClock.uptimeMillis(), writer);
                    writer.println();
        }
    }
}
