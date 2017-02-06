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

package android.support.v17.leanback.widget;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v17.leanback.test.R;
import android.support.v17.leanback.widget.picker.DatePicker;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class DatePickerTest {

    private static final String TAG = "DatePickerTest";
    private static final long TRANSITION_LENGTH = 1000;

    Context mContext;
    View mViewAbove;
    DatePicker mDatePickerView;
    ViewGroup mDatePickerInnerView;
    View mViewBelow;

    @Rule
    public ActivityTestRule<DatePickerActivity> mActivityTestRule =
            new ActivityTestRule<>(DatePickerActivity.class, false, false);
    private DatePickerActivity mActivity;

    public void initActivity(Intent intent) throws Throwable {
        mActivity = mActivityTestRule.launchActivity(intent);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDatePickerView = (DatePicker) mActivity.findViewById(R.id.date_picker);
        mDatePickerInnerView = (ViewGroup) mDatePickerView.findViewById(R.id.picker);
        mDatePickerView.setActivatedVisibleItemCount(3);
        mDatePickerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDatePickerView.setActivated(!mDatePickerView.isActivated());
            }
        });
        if (intent.getIntExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets) == R.layout.datepicker_with_other_widgets) {
            mViewAbove = mActivity.findViewById(R.id.above_picker);
            mViewBelow = mActivity.findViewById(R.id.below_picker);
        } else if (intent.getIntExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets) == R.layout.datepicker_alone) {
            // A layout with only a DatePicker widget that is initially activated.
            mActivityTestRule.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDatePickerView.setActivated(true);
                }
            });
            Thread.sleep(500);
        }
    }

    @Test
    public void testFocusTravel() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets);
        initActivity(intent);

        assertThat("TextView above should have focus initially", mViewAbove.hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("DatePicker should have focus now", mDatePickerView.isFocused(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The first column of DatePicker should hold focus",
                mDatePickerInnerView.getChildAt(0).hasFocus(), is(true));

        // skipping the separator in the child indices
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The second column of DatePicker should hold focus",
                mDatePickerInnerView.getChildAt(2).hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The third column of DatePicker should hold focus",
                mDatePickerInnerView.getChildAt(4).hasFocus(), is(true));
    }

    @Test
    public void testFocusRetainedForASelectedColumn()
            throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets);
        initActivity(intent);
        mDatePickerView.setFocusable(true);
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);

        assertThat("DatePicker should have focus when it's focusable",
                mDatePickerView.isFocused(), is(true));


        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("After the first activation, the first column of DatePicker should hold focus",
                mDatePickerInnerView.getChildAt(0).hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);

        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The third column of DatePicker should hold focus",
                mDatePickerInnerView.getChildAt(4).hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("After the first deactivation, the DatePicker itself should hold focus",
                mDatePickerView.isFocused(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("After the second activation, the last selected column (3rd) should hold focus",
                mDatePickerInnerView.getChildAt(4).hasFocus(), is(true));
    }

    @Test
    public void testFocusSkippedWhenDatePickerUnFocusable()
            throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets);
        initActivity(intent);

        mDatePickerView.setFocusable(false);
        assertThat("TextView above should have focus initially.", mViewAbove.hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);

        assertThat("DatePicker should be skipped and TextView below should have focus.",
                mViewBelow.hasFocus(), is(true));
    }

    @Test
    public void testTemporaryFocusLossWhenDeactivated()
            throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets);
        initActivity(intent);

        final int[] currentFocusChangeCountForViewAbove = {0};
        mDatePickerView.setFocusable(true);
        Log.d(TAG, "view above: " + mViewAbove);
        mViewAbove.setOnFocusChangeListener(new View.OnFocusChangeListener(){
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                currentFocusChangeCountForViewAbove[0]++;
            }
        });
        assertThat("TextView above should have focus initially.", mViewAbove.hasFocus(), is(true));

        // Traverse to the third column of date picker
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        // Click once to activate
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        // Traverse to the third column
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        // Click to deactivate. Before that we remember the focus change count for the view above.
        // This view should NOT receive temporary focus when DatePicker is deactivated, and
        // DatePicker itself should capture the focus.
        int[] lastFocusChangeCountForViewAbove = {currentFocusChangeCountForViewAbove[0]};
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("DatePicker should have focus now since it's focusable",
                mDatePickerView.isFocused(), is(true));
        assertThat("Focus change count of view above should not be changed after last click.",
                currentFocusChangeCountForViewAbove[0], is(lastFocusChangeCountForViewAbove[0]));
    }

    @Test
    public void testTemporaryFocusLossWhenActivated() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_with_other_widgets);
        initActivity(intent);
        final int[] currentFocusChangeCountForColumns = {0, 0, 0};
        mDatePickerView.setFocusable(true);
        mDatePickerInnerView.getChildAt(0).setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        currentFocusChangeCountForColumns[0]++;
                    }
                });

        mDatePickerInnerView.getChildAt(2).setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        currentFocusChangeCountForColumns[1]++;
                    }
                });

        mDatePickerInnerView.getChildAt(4).setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        currentFocusChangeCountForColumns[2]++;
                    }
                });

        // Traverse to the third column of date picker
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        // Click once to activate
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        // Traverse to the third column
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        // Click to deactivate
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        // Click again. The focus should NOT be temporarily moved to the other columns and the third
        // column should receive focus.
        // Before that we will remember the last focus change count to compare it against after the
        // click.
        int[] lastFocusChangeCountForColumns = {currentFocusChangeCountForColumns[0],
                currentFocusChangeCountForColumns[1], currentFocusChangeCountForColumns[2]};
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("Focus change count of column 0 should not be changed after last click.",
                currentFocusChangeCountForColumns[0], is(lastFocusChangeCountForColumns[0]));
        assertThat("Focus change count of column 1 should not be changed after last click.",
                currentFocusChangeCountForColumns[1], is(lastFocusChangeCountForColumns[1]));
        assertThat("Focus change count of column 2 should not be changed after last click.",
                currentFocusChangeCountForColumns[2], is(lastFocusChangeCountForColumns[2]));
    }

    @Test
    public void testInitiallyActiveDatePicker()
            throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(DatePickerActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.datepicker_alone);
        initActivity(intent);

        assertThat("The first column of DatePicker should initially hold focus",
                mDatePickerInnerView.getChildAt(0).hasFocus(), is(true));

        // focus on first column
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The first column of DatePicker should still hold focus after scrolling down",
                mDatePickerInnerView.getChildAt(0).hasFocus(), is(true));

        // focus on second column
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The second column of DatePicker should hold focus after scrolling right",
                mDatePickerInnerView.getChildAt(2).hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The second column of DatePicker should still hold focus after scrolling down",
                mDatePickerInnerView.getChildAt(2).hasFocus(), is(true));

        // focus on third column
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The third column of DatePicker should hold focus after scrolling right",
                mDatePickerInnerView.getChildAt(4).hasFocus(), is(true));

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        Thread.sleep(TRANSITION_LENGTH);
        assertThat("The third column of DatePicker should still hold focus after scrolling down",
                mDatePickerInnerView.getChildAt(4).hasFocus(), is(true));
    }

    private void sendKeys(int ...keys) {
        for (int i = 0; i < keys.length; i++) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keys[i]);
        }
    }
}
