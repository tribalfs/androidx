/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.support.lifecycle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.support.annotation.Nullable;
import android.support.test.filters.SmallTest;

import com.android.support.executors.AppToolkitTaskExecutor;
import com.android.support.executors.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.processors.ReplayProcessor;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.AsyncSubject;

@SmallTest
public class LiveDataReactiveStreamsTest {
    private static final Lifecycle sLifecycle = new Lifecycle() {
        @Override
        public void addObserver(LifecycleObserver observer) {
        }

        @Override
        public void removeObserver(LifecycleObserver observer) {
        }

        @Override
        public int getCurrentState() {
            return Lifecycle.RESUMED;
        }
    };
    private static final LifecycleProvider sLifecycleProvider = new LifecycleProvider() {

        @Override
        public Lifecycle getLifecycle() {
            return sLifecycle;
        }

    };

    private final List<String> mLiveDataOutput = new ArrayList<>();
    private final Observer<String> mObserver = new Observer<String>() {
        @Override
        public void onChanged(@Nullable String s) {
            mLiveDataOutput.add(s);
        }
    };

    private final ReplayProcessor<String> mOutputProcessor = ReplayProcessor.create();

    private static final TestScheduler sBackgroundScheduler = new TestScheduler();
    private Thread mTestThread;

    @Before
    public void init() {
        mTestThread = Thread.currentThread();
        AppToolkitTaskExecutor.getInstance().setDelegate(new TaskExecutor() {

            @Override
            public void executeOnDiskIO(Runnable runnable) {
                throw new IllegalStateException();
            }

            @Override
            public void executeOnMainThread(final Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return Thread.currentThread() == mTestThread;
            }

        });
    }

    @After
    public void removeExecutorDelegate() {
        AppToolkitTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void convertsFromPublisher() {
        PublishProcessor<String> processor = PublishProcessor.create();
        LiveData<String> liveData = LiveDataReactiveStreams.fromPublisher(processor);

        liveData.observe(sLifecycleProvider, mObserver);

        processor.onNext("foo");
        processor.onNext("bar");
        processor.onNext("baz");

        assertThat(mLiveDataOutput, is(Arrays.asList("foo", "bar", "baz")));
    }

    @Test
    public void convertsFromPublisherWithMultipleObservers() {
        final List<String> output2 = new ArrayList<>();
        PublishProcessor<String> processor = PublishProcessor.create();
        LiveData<String> liveData = LiveDataReactiveStreams.fromPublisher(processor);

        liveData.observe(sLifecycleProvider, mObserver);

        processor.onNext("foo");
        processor.onNext("bar");

        // The second mObserver should only get the newest value and any later values.
        liveData.observe(sLifecycleProvider, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                output2.add(s);
            }
        });

        processor.onNext("baz");

        assertThat(mLiveDataOutput, is(Arrays.asList("foo", "bar", "baz")));
        assertThat(output2, is(Arrays.asList("bar", "baz")));
    }

    @Test
    public void convertsFromAsyncPublisher() {
        Flowable<String> input = Flowable.just("foo")
                .concatWith(Flowable.just("bar", "baz").observeOn(sBackgroundScheduler));
        LiveData<String> liveData = LiveDataReactiveStreams.fromPublisher(input);

        liveData.observe(sLifecycleProvider, mObserver);

        assertThat(mLiveDataOutput, is(Collections.singletonList("foo")));
        sBackgroundScheduler.triggerActions();
        assertThat(mLiveDataOutput, is(Arrays.asList("foo", "bar", "baz")));
    }

    @Test
    public void convertsToPublisherWithSyncData() {
        LiveData<String> liveData = new LiveData<>();
        liveData.setValue("foo");
        assertThat(liveData.getValue(), is("foo"));

        Flowable.fromPublisher(LiveDataReactiveStreams.toPublisher(sLifecycleProvider, liveData))
                .subscribe(mOutputProcessor);

        liveData.setValue("bar");
        liveData.setValue("baz");

        assertThat(
                mOutputProcessor.getValues(new String[]{}),
                is(new String[] {"foo", "bar", "baz"}));
    }

    @Test
    public void convertingToPublisherIsCancelable() {
        LiveData<String> liveData = new LiveData<>();
        liveData.setValue("foo");
        assertThat(liveData.getValue(), is("foo"));

        Disposable disposable = Flowable
                .fromPublisher(LiveDataReactiveStreams.toPublisher(sLifecycleProvider, liveData))
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        mLiveDataOutput.add(s);
                    }
                });

        liveData.setValue("bar");
        liveData.setValue("baz");

        assertThat(liveData.getObserverCount(), is(1));
        disposable.dispose();

        liveData.setValue("fizz");
        liveData.setValue("buzz");

        assertThat(mLiveDataOutput, is(Arrays.asList("foo", "bar", "baz")));
        // Canceling disposable should also remove livedata mObserver.
        assertThat(liveData.getObserverCount(), is(0));
    }

    @Test
    public void convertsToPublisherWithBackpressure() {
        LiveData<String> liveData = new LiveData<>();

        final AsyncSubject<Subscription> subscriptionSubject = AsyncSubject.create();

        Flowable.fromPublisher(LiveDataReactiveStreams.toPublisher(sLifecycleProvider, liveData))
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriptionSubject.onNext(s);
                        subscriptionSubject.onComplete();
                    }

                    @Override
                    public void onNext(String s) {
                        mOutputProcessor.onNext(s);
                    }

                    @Override
                    public void onError(Throwable t) {
                        throw new RuntimeException(t);
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        // Subscription should have happened synchronously. If it didn't, this will deadlock.
        final Subscription subscription = subscriptionSubject.blockingSingle();

        subscription.request(1);
        assertThat(mOutputProcessor.getValues(new String[]{}), is(new String[] {}));

        liveData.setValue("foo");
        assertThat(mOutputProcessor.getValues(new String[]{}), is(new String[] {"foo"}));

        subscription.request(2);
        liveData.setValue("baz");
        liveData.setValue("fizz");

        assertThat(
                mOutputProcessor.getValues(new String[]{}),
                is(new String[] {"foo", "baz", "fizz"}));

        // 'nyan' will be dropped as there is nothing currently requesting a stream.
        liveData.setValue("nyan");
        liveData.setValue("cat");

        assertThat(
                mOutputProcessor.getValues(new String[]{}),
                is(new String[] {"foo", "baz", "fizz"}));

        // When a new request comes in, the latest value will be pushed.
        subscription.request(1);
        assertThat(
                mOutputProcessor.getValues(new String[]{}),
                is(new String[] {"foo", "baz", "fizz", "cat"}));
    }

    @Test
    public void convertsToPublisherWithAsyncData() {
        LiveData<String> liveData = new LiveData<>();

        Flowable.fromPublisher(LiveDataReactiveStreams.toPublisher(sLifecycleProvider, liveData))
                .observeOn(sBackgroundScheduler)
                .subscribe(mOutputProcessor);

        liveData.setValue("foo");

        assertThat(mOutputProcessor.getValues(new String[]{}), is(new String[] {}));
        sBackgroundScheduler.triggerActions();
        assertThat(mOutputProcessor.getValues(new String[]{}), is(new String[] {"foo"}));

        liveData.setValue("bar");
        liveData.setValue("baz");

        assertThat(mOutputProcessor.getValues(new String[]{}), is(new String[] {"foo"}));
        sBackgroundScheduler.triggerActions();
        assertThat(mOutputProcessor.getValues(
                new String[]{}),
                is(new String[] {"foo", "bar", "baz"}));
    }
}
