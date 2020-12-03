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

package androidx.appcompat.widget;

import static androidx.appcompat.testutils.TestUtilsMatchers.asViewMatcher;
import static androidx.appcompat.testutils.TestUtilsMatchers.thumbColor;
import static androidx.appcompat.testutils.TestUtilsMatchers.trackColor;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Typeface;
import android.os.Build;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.appcompat.test.R;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Provides tests specific to {@link SwitchCompat} class.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SwitchCompatTest {

    @Rule
    public final ActivityTestRule<SwitchCompatActivity> mActivityTestRule =
            new ActivityTestRule<>(SwitchCompatActivity.class);
    private SwitchCompatActivity mActivity;
    private ViewGroup mContainer;

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
        mContainer = mActivity.findViewById(androidx.appcompat.test.R.id.container);
    }

    @Test
    public void testFontResources() {
        SwitchCompat switchButton = mContainer.findViewById(R.id.switch_fontresource);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplefont);

        assertEquals(expected, switchButton.getTypeface());
    }

    @Test
    public void testTint() {
        // Given a switch with tints set for the track and thumb
        final int expectedThumbTint = 0xffff00ff;
        final int expectedTrackTint = 0xff00ffff;

        // Then the tints should be applied
        onView(withId(R.id.switch_tint))
                .check(matches(asViewMatcher(thumbColor(expectedThumbTint))));
        onView(withId(R.id.switch_tint))
                .check(matches(asViewMatcher(trackColor(expectedTrackTint))));
    }

    @Test
    public void testAccessibility_default() {
        SwitchCompat switchButton = mContainer.findViewById(R.id.switch_tint);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        switchButton.onInitializeAccessibilityNodeInfo(info);
        assertEquals("android.widget.Switch", info.getClassName());
        final String capitalOff =
                mActivity.getResources().getString(androidx.appcompat.R.string.abc_capital_off);
        final String text = mActivity.getResources().getString(R.string.sample_text1);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            assertEquals(text + " " + capitalOff, info.getText());
            assertNull(ViewCompat.getStateDescription(switchButton));
        } else {
            assertEquals(text, info.getText());
            assertEquals(capitalOff, ViewCompat.getStateDescription(switchButton));
        }
        info.recycle();
    }

    @Test
    public void testAccessibility_textOnOff() {
        final SwitchCompat switchButton = mContainer.findViewById(R.id.switch_textOnOff);
        final CharSequence textOn = "testStateOn";
        final CharSequence textOff = "testStateOff";
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        switchButton.onInitializeAccessibilityNodeInfo(info);
        assertEquals("android.widget.Switch", info.getClassName());
        final String text = mActivity.getResources().getString(R.string.sample_text1);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            assertEquals(text + " " + textOff, info.getText());
            assertNull(ViewCompat.getStateDescription(switchButton));
        } else {
            assertEquals(text, info.getText());
            assertEquals(textOff, ViewCompat.getStateDescription(switchButton));
        }
        info.recycle();

        final CharSequence newTextOff = "new text off";
        final CharSequence newTextOn = "new text on";
        mActivity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        switchButton.toggle();
                        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                        switchButton.onInitializeAccessibilityNodeInfo(info);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            assertEquals(text + " " + textOn, info.getText());
                            assertNull(ViewCompat.getStateDescription(switchButton));
                        } else {
                            assertEquals(text, info.getText());
                            assertEquals(textOn, ViewCompat.getStateDescription(switchButton));
                        }
                        info.recycle();

                        switchButton.setTextOn(newTextOn);
                        switchButton.setTextOff(newTextOff);
                        info = AccessibilityNodeInfo.obtain();
                        switchButton.onInitializeAccessibilityNodeInfo(info);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            assertEquals(text + " " + newTextOn, info.getText());
                            assertNull(ViewCompat.getStateDescription(switchButton));
                        } else {
                            assertEquals(text, info.getText());
                            assertEquals(newTextOn, ViewCompat.getStateDescription(switchButton));
                        }
                        info.recycle();

                        switchButton.toggle();
                        info = AccessibilityNodeInfo.obtain();
                        switchButton.onInitializeAccessibilityNodeInfo(info);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            assertEquals(text + " " + newTextOff, info.getText());
                            assertNull(ViewCompat.getStateDescription(switchButton));
                        } else {
                            assertEquals(text, info.getText());
                            assertEquals(newTextOff, ViewCompat.getStateDescription(switchButton));
                        }
                        info.recycle();
                    }
                }
        );
    }
}
