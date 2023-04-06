/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.tiles.timeline;

import static android.os.Looper.getMainLooper;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.wear.tiles.TilesTestRunner;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(TilesTestRunner.class)
@DoNotInstrument
public class TilesTimelineManagerTest {
    private static final int TOKEN = 5;

    @Rule public Expect expect = Expect.create();

    private AlarmManager mAlarmManager;
    private long mCurrentTime;
    private final TilesTimelineManager.Clock mFakeClock = () -> mCurrentTime;
    private TilesTimelineManager mTimelineManager;
    private final Executor mMainThreadExecutor =
            ContextCompat.getMainExecutor(getApplicationContext());

    @Before
    public void setUp() {
        mAlarmManager =
                (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        // Sync the clock with the current time. This is really just to ensure that the test times
        // we're going to use are in the future, which prevents AlarmManager (which always uses the
        // system time :( ) from immediately firing all our alarms.
        mCurrentTime = System.currentTimeMillis();

        mTimelineManager = null;
    }

    @After
    public void tearDown() {
        if (mTimelineManager != null) {
            mTimelineManager.close();
        }
    }

    @Test
    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    public void timelineManager_singleTileImmediatelySet() {
        List<androidx.wear.tiles.LayoutElementBuilders.Layout> returnedLayouts = new ArrayList<>();
        androidx.wear.tiles.LayoutElementBuilders.Layout layout = buildTextLayout("Hello World");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout)
                        .build();
        androidx.wear.tiles.TimelineBuilders.Timeline timeline =
                new androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(entry)
                        .build();

        mTimelineManager =
                new TilesTimelineManager(
                        mAlarmManager,
                        mFakeClock,
                        timeline,
                        TOKEN,
                        mMainThreadExecutor,
                        (index, element) -> returnedLayouts.add(element));
        mTimelineManager.init();
        shadowOf(getMainLooper()).idle();

        assertThat(returnedLayouts).hasSize(1);
        expectLayoutsEqual(returnedLayouts.get(0), layout);
        verifyNoAlarmsSet();
    }

    @Test
    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    public void timelineManager_tileWithRollover() {
        List<androidx.wear.tiles.LayoutElementBuilders.Layout> returnedLayouts = new ArrayList<>();
        final long cutover1Millis = mCurrentTime + Duration.ofMinutes(10).toMillis();
        final long cutover2Millis = mCurrentTime + Duration.ofMinutes(20).toMillis();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout1 = buildTextLayout("Tile1");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry1 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout1)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(0)
                                        .setEndMillis(cutover1Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout2 = buildTextLayout("Tile2");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry2 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout2)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover1Millis)
                                        .setEndMillis(cutover2Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout3 = buildTextLayout("Tile3");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry3 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout3)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover2Millis)
                                        .setEndMillis(Long.MAX_VALUE)
                                        .build())
                        .build();

        androidx.wear.tiles.TimelineBuilders.Timeline timeline =
                new androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(entry1)
                        .addTimelineEntry(entry2)
                        .addTimelineEntry(entry3)
                        .build();

        mTimelineManager =
                new TilesTimelineManager(
                        mAlarmManager,
                        mFakeClock,
                        timeline,
                        TOKEN,
                        mMainThreadExecutor,
                        (index, element) -> returnedLayouts.add(element));
        mTimelineManager.init();
        shadowOf(getMainLooper()).idle();

        assertThat(returnedLayouts).hasSize(1);
        expectLayoutsEqual(returnedLayouts.get(0), layout1);

        // Just before the cut (ensure it's working properly)
        assertThat(returnedLayouts).hasSize(1);

        // Seek to the cutover time.
        seekToTime(cutover1Millis);
        assertThat(returnedLayouts).hasSize(2);
        expectLayoutsEqual(returnedLayouts.get(1), layout2);

        seekToTime(cutover2Millis);
        assertThat(returnedLayouts).hasSize(3);
        expectLayoutsEqual(returnedLayouts.get(2), layout3);

        verifyNoAlarmsSet();
    }

    @Test
    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    public void timelineManager_alarmsCanceledOnDeInit() {
        List<androidx.wear.tiles.LayoutElementBuilders.Layout> returnedLayouts = new ArrayList<>();
        final long cutover1Millis = mCurrentTime + Duration.ofMinutes(10).toMillis();

        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry1 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(buildTextLayout("Tile1"))
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(0)
                                        .setEndMillis(cutover1Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry2 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(buildTextLayout("Tile2"))
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover1Millis)
                                        .setEndMillis(Long.MAX_VALUE)
                                        .build())
                        .build();

        androidx.wear.tiles.TimelineBuilders.Timeline timeline =
                new androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(entry1)
                        .addTimelineEntry(entry2)
                        .build();

        mTimelineManager =
                new TilesTimelineManager(
                        mAlarmManager,
                        mFakeClock,
                        timeline,
                        TOKEN,
                        mMainThreadExecutor,
                        (index, element) -> returnedLayouts.add(element));
        mTimelineManager.init();
        shadowOf(getMainLooper()).idle();

        // Alarms should be set (as verified in previous test)
        mTimelineManager.close();

        verifyNoAlarmsSet();
    }

    @Test
    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    public void timelineManager_minDelayEnforced() {
        List<androidx.wear.tiles.LayoutElementBuilders.Layout> returnedLayouts = new ArrayList<>();

        final long cutover1Millis =
                mCurrentTime + TilesTimelineManager.MIN_TILE_UPDATE_DELAY_MILLIS / 2;

        androidx.wear.tiles.LayoutElementBuilders.Layout layout1 = buildTextLayout("Tile1");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry1 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout1)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(0)
                                        .setEndMillis(cutover1Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout2 = buildTextLayout("Tile2");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry2 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout2)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover1Millis)
                                        .setEndMillis(Long.MAX_VALUE)
                                        .build())
                        .build();

        androidx.wear.tiles.TimelineBuilders.Timeline timeline =
                new androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(entry1)
                        .addTimelineEntry(entry2)
                        .build();

        mTimelineManager =
                new TilesTimelineManager(
                        mAlarmManager,
                        mFakeClock,
                        timeline,
                        TOKEN,
                        mMainThreadExecutor,
                        (index, element) -> returnedLayouts.add(element));
        mTimelineManager.init();
        shadowOf(getMainLooper()).idle();

        // Fast forward to the "expected" cutover time.
        seekToTime(cutover1Millis);

        assertThat(returnedLayouts).hasSize(1);
        expectLayoutsEqual(returnedLayouts.get(0), layout1);

        // Seek to the end of the "min delay" period
        seekToTime(cutover1Millis + TilesTimelineManager.MIN_TILE_UPDATE_DELAY_MILLIS / 2);
        assertThat(returnedLayouts).hasSize(2);
        expectLayoutsEqual(returnedLayouts.get(1), layout2);
    }

    @Test
    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    public void timelineManager_minDelayUsesCorrectEntry() {
        // This has three entries, one initial one, one that happens after MIN_DELAY/2, and one that
        // happens after MIN_DELAY. This should totally skip the middle entry, and only show the
        // first and last entries.
        List<androidx.wear.tiles.LayoutElementBuilders.Layout> returnedLayouts = new ArrayList<>();

        final long cutover1Millis =
                mCurrentTime + TilesTimelineManager.MIN_TILE_UPDATE_DELAY_MILLIS / 2;
        final long cutover2Millis =
                cutover1Millis + TilesTimelineManager.MIN_TILE_UPDATE_DELAY_MILLIS / 2;

        androidx.wear.tiles.LayoutElementBuilders.Layout layout1 = buildTextLayout("Tile1");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry1 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout1)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(0)
                                        .setEndMillis(cutover1Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout2 = buildTextLayout("Tile2");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry2 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout2)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover1Millis)
                                        .setEndMillis(cutover2Millis)
                                        .build())
                        .build();

        androidx.wear.tiles.LayoutElementBuilders.Layout layout3 = buildTextLayout("Tile3");
        androidx.wear.tiles.TimelineBuilders.TimelineEntry entry3 =
                new androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout3)
                        .setValidity(
                                new androidx.wear.tiles.TimelineBuilders.TimeInterval.Builder()
                                        .setStartMillis(cutover2Millis)
                                        .setEndMillis(Long.MAX_VALUE)
                                        .build())
                        .build();

        androidx.wear.tiles.TimelineBuilders.Timeline timeline =
                new androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(entry1)
                        .addTimelineEntry(entry2)
                        .addTimelineEntry(entry3)
                        .build();

        mTimelineManager =
                new TilesTimelineManager(
                        mAlarmManager,
                        mFakeClock,
                        timeline,
                        TOKEN,
                        mMainThreadExecutor,
                        (index, element) -> returnedLayouts.add(element));
        mTimelineManager.init();
        shadowOf(getMainLooper()).idle();

        // Fast forward to the "expected" cutover time.
        seekToTime(cutover2Millis);

        assertThat(returnedLayouts).hasSize(2);
        expectLayoutsEqual(returnedLayouts.get(0), layout1);
        expectLayoutsEqual(returnedLayouts.get(1), layout3);
    }

    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    private static androidx.wear.tiles.LayoutElementBuilders.Layout buildTextLayout(String text) {
        return new androidx.wear.tiles.LayoutElementBuilders.Layout.Builder()
                .setRoot(
                        new androidx.wear.tiles.LayoutElementBuilders.Text.Builder()
                                .setText(text)
                                .build())
                .build();
    }

    private void seekToTime(long timeMillis) {
        ShadowAlarmManager shadowAlarmManager = shadowOf(mAlarmManager);

        // Trigger all set alarms until the given time.
        while (shadowAlarmManager.peekNextScheduledAlarm() != null
                && shadowAlarmManager.peekNextScheduledAlarm().triggerAtTime <= timeMillis) {
            // Jump to that time
            ScheduledAlarm nextScheduledAlarm = shadowAlarmManager.getNextScheduledAlarm();
            mCurrentTime = nextScheduledAlarm.triggerAtTime;
            nextScheduledAlarm.onAlarmListener.onAlarm();
            shadowOf(getMainLooper()).idle();
        }

        mCurrentTime = timeMillis;
    }

    // This is a little hacky, but there's no cleaner way to assert that it hasn't set any rollover
    // alarms.
    private void verifyNoAlarmsSet() {
        ShadowAlarmManager shadowAlarmManager = shadowOf(mAlarmManager);
        expect.that(shadowAlarmManager.getScheduledAlarms()).isEmpty();
    }

    @SuppressWarnings("deprecation") // TODO(b/276343540): Use protolayout types
    private void expectLayoutsEqual(
            androidx.wear.tiles.LayoutElementBuilders.Layout actual,
            androidx.wear.tiles.LayoutElementBuilders.Layout expected) {
        expect.that(actual.toProto()).isEqualTo(expected.toProto());
    }
}
