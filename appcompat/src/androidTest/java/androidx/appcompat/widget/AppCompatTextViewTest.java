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
package androidx.appcompat.widget;

import static androidx.appcompat.testutils.TestUtilsActions.setEnabled;
import static androidx.appcompat.testutils.TestUtilsActions.setTextAppearance;
import static androidx.appcompat.testutils.TestUtilsMatchers.isBackground;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.PrecomputedText;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.GuardedBy;
import androidx.appcompat.test.R;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.text.PrecomputedTextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In addition to all tinting-related tests done by the base class, this class provides
 * tests specific to {@link AppCompatTextView} class.
 */
@SmallTest
public class AppCompatTextViewTest
        extends AppCompatBaseViewTest<AppCompatTextViewActivity, AppCompatTextView> {
    private static final String SAMPLE_TEXT_1 = "Hello, World!";
    private static final String SAMPLE_TEXT_2 = "Hello, Android!";
    private static final int UNLIMITED_MEASURE_SPEC = View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED);

    public AppCompatTextViewTest() {
        super(AppCompatTextViewActivity.class);
    }

    /**
     * This method tests that background tinting is applied when the call to
     * {@link androidx.core.view.ViewCompat#setBackgroundTintList(View, ColorStateList)}
     * is done as a deferred event.
     */
    @Test
    @MediumTest
    public void testDeferredBackgroundTinting() throws Throwable {
        onView(withId(R.id.view_untinted_deferred))
                .check(matches(isBackground(0xff000000, true)));

        final @ColorInt int oceanDefault = ResourcesCompat.getColor(
                mResources, R.color.ocean_default, null);

        final ColorStateList oceanColor = ResourcesCompat.getColorStateList(
                mResources, R.color.color_state_list_ocean, null);

        // Emulate delay in kicking off the call to ViewCompat.setBackgroundTintList
        Thread.sleep(200);
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView view = mActivity.findViewById(R.id.view_untinted_deferred);
                ViewCompat.setBackgroundTintList(view, oceanColor);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // Check that the background has switched to the matching entry in the newly set
        // color state list.
        onView(withId(R.id.view_untinted_deferred))
                .check(matches(isBackground(oceanDefault, true)));
    }

    @Test
    public void testAllCaps() {
        final String text1 = mResources.getString(R.string.sample_text1);
        final String text2 = mResources.getString(R.string.sample_text2);

        final AppCompatTextView textView1 = mContainer.findViewById(R.id.text_view_caps1);
        final AppCompatTextView textView2 = mContainer.findViewById(R.id.text_view_caps2);

        // Note that TextView.getText() returns the original text. We are interested in
        // the transformed text that is set on the Layout object used to draw the final
        // (transformed) content.
        assertEquals("Text view starts in all caps on",
                text1.toUpperCase(), textView1.getLayout().getText().toString());
        assertEquals("Text view starts in all caps off",
                text2, textView2.getLayout().getText().toString());

        // Toggle all-caps mode on the two text views
        onView(withId(R.id.text_view_caps1)).perform(
                setTextAppearance(R.style.TextStyleAllCapsOff));
        assertEquals("Text view is now in all caps off",
                text1, textView1.getLayout().getText().toString());

        onView(withId(R.id.text_view_caps2)).perform(
                setTextAppearance(R.style.TextStyleAllCapsOn));
        assertEquals("Text view is now in all caps on",
                text2.toUpperCase(), textView2.getLayout().getText().toString());
    }

    @Test
    public void testAppCompatAllCapsFalseOnButton() {
        final String text = mResources.getString(R.string.sample_text2);
        final AppCompatTextView textView =
                 mContainer.findViewById(R.id.text_view_app_allcaps_false);

        assertEquals("Text view is not in all caps", text, textView.getLayout().getText());
    }

    @Test
    public void testTextColorSetHex() {
        final TextView textView =  mContainer.findViewById(R.id.view_text_color_hex);
        assertEquals(Color.RED, textView.getCurrentTextColor());
    }

    @Test
    public void testTextColorSetColorStateList() {
        final TextView textView =  mContainer.findViewById(R.id.view_text_color_csl);

        onView(withId(R.id.view_text_color_csl)).perform(setEnabled(true));
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.ocean_default),
                textView.getCurrentTextColor());

        onView(withId(R.id.view_text_color_csl)).perform(setEnabled(false));
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.ocean_disabled),
                textView.getCurrentTextColor());
    }

    @Test
    public void testTextColorSetThemeAttrHex() {
        final TextView textView =  mContainer.findViewById(R.id.view_text_color_primary);
        assertEquals(Color.BLUE, textView.getCurrentTextColor());
    }

    @Test
    public void testTextColorSetThemeAttrColorStateList() {
        final TextView textView =  mContainer.findViewById(R.id.view_text_color_secondary);

        onView(withId(R.id.view_text_color_secondary)).perform(setEnabled(true));
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.sand_default),
                textView.getCurrentTextColor());

        onView(withId(R.id.view_text_color_secondary)).perform(setEnabled(false));
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.sand_disabled),
                textView.getCurrentTextColor());
    }

    private void verifyTextLinkColor(TextView textView) {
        ColorStateList linkColorStateList = textView.getLinkTextColors();
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.lilac_default),
                linkColorStateList.getColorForState(new int[] { android.R.attr.state_enabled}, 0));
        assertEquals(ContextCompat.getColor(textView.getContext(), R.color.lilac_disabled),
                linkColorStateList.getColorForState(new int[] { -android.R.attr.state_enabled}, 0));
    }

    @Test
    public void testTextLinkColor() {
        verifyTextLinkColor((TextView) mContainer.findViewById(R.id.view_text_link_enabled));
        verifyTextLinkColor((TextView) mContainer.findViewById(R.id.view_text_link_disabled));
    }

    @Test
    @UiThreadTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
    public void testTextSize_canBeZero() {
        final TextView textView = mContainer.findViewById(R.id.textview_zero_text_size);
        // text size should be 0 as set in xml, rather than the text view default (15.0)
        assertEquals(0.0f, textView.getTextSize(), 0.01f);
        // text size can be set programmatically to non-negative values
        textView.setTextSize(20f);
        assertTrue(textView.getTextSize() > 0.0f);
        // text size should become 0 when we set a text appearance with 0 text size
        TextViewCompat.setTextAppearance(textView, R.style.TextView_ZeroTextSize);
        assertEquals(0f, textView.getTextSize(), 0.01f);
    }

    @Test
    public void testFontResources_setInStringFamilyName() {
        TextView textView =
                mContainer.findViewById(R.id.textview_fontresource_fontfamily_string_resource);
        assertNotNull(textView.getTypeface());
        // Pre-L, Typeface always resorts to native for a Typeface object, hence giving you a
        // different one each call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            assertEquals(Typeface.SANS_SERIF, textView.getTypeface());
        }
        textView = mContainer.findViewById(R.id.textview_fontresource_fontfamily_string_direct);
        assertNotNull(textView.getTypeface());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            assertEquals(Typeface.SANS_SERIF, textView.getTypeface());
        }
    }

    @Test
    public void testFontResources_setInXmlFamilyName() {
        TextView textView = mContainer.findViewById(R.id.textview_fontresource_fontfamily);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplefont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlFamilyName() {
        TextView textView = mContainer.findViewById(R.id.textview_fontxmlresource_fontfamily);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplexmlfont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlFamilyNameWithTextStyle() {
        TextView textView =
                mContainer.findViewById(R.id.textview_fontxmlresource_fontfamily_textstyle);

        assertNotEquals(Typeface.DEFAULT, textView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlFamilyNameWithTextStyle2() {
        TextView textView =
                mContainer.findViewById(R.id.textview_fontxmlresource_fontfamily_textstyle2);

        assertNotEquals(Typeface.DEFAULT, textView.getTypeface());
    }

    @Test
    public void testFontResources_setInXmlStyle() {
        TextView textView = mContainer.findViewById(R.id.textview_fontresource_style);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplefont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlStyle() {
        TextView textView = mContainer.findViewById(R.id.textview_fontxmlresource_style);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplexmlfont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testFontResources_setInXmlTextAppearance() {
        TextView textView = mContainer.findViewById(R.id.textview_fontresource_textAppearance);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplefont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlTextAppearance() {
        TextView textView = mContainer.findViewById(R.id.textview_fontxmlresource_textAppearance);
        Typeface expected = ResourcesCompat.getFont(mActivity, R.font.samplexmlfont);

        assertEquals(expected, textView.getTypeface());
    }

    @Test
    public void testTextStyle_setTextStyleInStyle() {
        // TextView has a TextAppearance by default, but the textStyle can be overriden in style.
        TextView textView = mContainer.findViewById(R.id.textview_textStyleOverride);

        assertEquals(Typeface.ITALIC, textView.getTypeface().getStyle());
    }

    @Test
    public void testTextStyle_setTextStyleDirectly() {
        TextView textView = mContainer.findViewById(R.id.textview_textStyleDirect);

        assertEquals(Typeface.ITALIC, textView.getTypeface().getStyle());
    }

    @Test
    @UiThreadTest
    public void testFontResources_setTextAppearance() {
        TextView textView = mContainer.findViewById(R.id.textview_simple);

        TextViewCompat.setTextAppearance(textView, R.style.TextView_FontResourceWithStyle);

        assertNotEquals(Typeface.DEFAULT, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testSetTextAppearance_resetTypeface() throws PackageManager.NameNotFoundException {
        TextView textView = mContainer.findViewById(R.id.textview_simple);

        TextViewCompat.setTextAppearance(textView, R.style.TextView_SansSerif);
        Typeface firstTypeface = textView.getTypeface();

        TextViewCompat.setTextAppearance(textView, R.style.TextView_Serif);
        Typeface secondTypeface = textView.getTypeface();
        assertNotNull(firstTypeface);
        assertNotNull(secondTypeface);
        assertNotEquals(firstTypeface, secondTypeface);
    }

    @Test
    @UiThreadTest
    public void testTypefaceAttribute_serif() {
        TextView textView = mContainer.findViewById(R.id.textview_simple);

        TextViewCompat.setTextAppearance(textView, R.style.TextView_Typeface_Serif);

        assertEquals(Typeface.SERIF, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testTypefaceAttribute_monospace() {
        TextView textView = mContainer.findViewById(R.id.textview_simple);

        TextViewCompat.setTextAppearance(textView, R.style.TextView_Typeface_Monospace);

        assertEquals(Typeface.MONOSPACE, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testTypefaceAttribute_serifFromXml() {
        TextView textView = mContainer.findViewById(R.id.textview_typeface_serif);

        assertEquals(Typeface.SERIF, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testTypefaceAttribute_monospaceFromXml() {
        TextView textView = mContainer.findViewById(R.id.textview_typeface_monospace);

        assertEquals(Typeface.MONOSPACE, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testTypefaceAttribute_fontFamilyHierarchy() {
        // This view has typeface=serif set on the view directly and a fontFamily on the appearance
        TextView textView = mContainer.findViewById(R.id.textview_typeface_and_fontfamily);

        assertEquals(Typeface.SERIF, textView.getTypeface());
    }

    @SdkSuppress(minSdkVersion = 16)
    @Test
    @UiThreadTest
    public void testfontFamilyNamespaceHierarchy() {
        // This view has fontFamily set in both the app and android namespace. App should be used.
        TextView textView = mContainer.findViewById(R.id.textview_app_and_android_fontfamily);

        assertEquals(Typeface.MONOSPACE, textView.getTypeface());
    }

    @Test
    @UiThreadTest
    public void testBaselineAttributes() {
        TextView textView = mContainer.findViewById(R.id.textview_baseline);

        final int firstBaselineToTopHeight = textView.getResources()
                .getDimensionPixelSize(R.dimen.textview_firstBaselineToTopHeight);
        final int lastBaselineToBottomHeight = textView.getResources()
                .getDimensionPixelSize(R.dimen.textview_lastBaselineToBottomHeight);
        final int lineHeight = textView.getResources()
                .getDimensionPixelSize(R.dimen.textview_lineHeight);

        assertEquals(firstBaselineToTopHeight,
                TextViewCompat.getFirstBaselineToTopHeight(textView));
        assertEquals(lastBaselineToBottomHeight,
                TextViewCompat.getLastBaselineToBottomHeight(textView));
        assertEquals(lineHeight, textView.getLineHeight());
    }

    private static class ManualExecutor implements Executor {
        private static final int TIMEOUT_MS = 5000;
        private final Lock mLock = new ReentrantLock();
        private final Condition mCond = mLock.newCondition();

        @GuardedBy("mLock")
        ArrayList<Runnable> mRunnables = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            mLock.lock();
            try {
                mRunnables.add(command);
                mCond.signal();
            } finally {
                mLock.unlock();
            }
        }

        /**
         * Synchronously execute the i-th runnable
         * @param i
         */
        public void doExecution(int i) {
            getRunnableAt(i).run();
        }

        private Runnable getRunnableAt(int i) {
            mLock.lock();
            try {
                long remainingNanos = TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
                while (mRunnables.size() <= i) {
                    try {
                        remainingNanos = mCond.awaitNanos(remainingNanos);
                        if (remainingNanos <= 0) {
                            throw new RuntimeException("Timeout during waiting runnables.");
                        }
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                return mRunnables.get(i);
            } finally {
                mLock.unlock();
            }
        }
    }

    @Test
    public void testSetTextFuture() throws Throwable {
        final ManualExecutor executor = new ManualExecutor();

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.setText(""); // Make the measured width to be zero.
                tv.setTextFuture(PrecomputedTextCompat.getTextFuture(
                        SAMPLE_TEXT_1, tv.getTextMetricsParamsCompat(), executor));
            }
        });

        executor.doExecution(0);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.measure(UNLIMITED_MEASURE_SPEC, UNLIMITED_MEASURE_SPEC);

                // 0 is a initial value of the text view width of this test. setTextFuture should
                // block and set text inside measure method. So, the result of measurment should not
                // be zero
                assertNotEquals(0.0f, tv.getMeasuredWidth());
                // setText may wrap the given text with SpannedString. Check the contents by casting
                // to String.
                assertEquals(SAMPLE_TEXT_1, tv.getText().toString());
                if (Build.VERSION.SDK_INT >= 28) {
                    assertTrue(tv.getText() instanceof PrecomputedText);
                }
            }
        });
    }

    @Test
    public void testSetTextAsync_getTextBlockingTest() throws Throwable {
        final ManualExecutor executor = new ManualExecutor();
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.setText(""); // Make the measured width to be zero.
                tv.setTextFuture(PrecomputedTextCompat.getTextFuture(
                        SAMPLE_TEXT_1, tv.getTextMetricsParamsCompat(), executor));
                tv.measure(UNLIMITED_MEASURE_SPEC, UNLIMITED_MEASURE_SPEC);
                assertNotEquals(0.0f, tv.getMeasuredWidth());
                assertEquals(SAMPLE_TEXT_1, tv.getText().toString());
                if (Build.VERSION.SDK_INT >= 28) {
                    assertTrue(tv.getText() instanceof PrecomputedText);
                }
            }
        });
        executor.doExecution(0);
    }

    @Test
    public void testSetTextAsync_executionOrder() throws Throwable {
        final ManualExecutor executor = new ManualExecutor();
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.setText(""); // Make the measured width to be zero.
                tv.setTextFuture(PrecomputedTextCompat.getTextFuture(
                        SAMPLE_TEXT_1, tv.getTextMetricsParamsCompat(), executor));
                tv.setTextFuture(PrecomputedTextCompat.getTextFuture(
                        SAMPLE_TEXT_2, tv.getTextMetricsParamsCompat(), executor));
            }
        });
        executor.doExecution(1);  // Do execution of 2nd runnable.
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.measure(UNLIMITED_MEASURE_SPEC, UNLIMITED_MEASURE_SPEC);
                assertNotEquals(0.0f, tv.getMeasuredWidth());
                // setText may wrap the given text with SpannedString. Check the contents by casting
                // to String.
                assertEquals(SAMPLE_TEXT_2, tv.getText().toString());
                if (Build.VERSION.SDK_INT >= 28) {
                    assertTrue(tv.getText() instanceof PrecomputedText);
                }
            }
        });
        executor.doExecution(0);  // Do execution of 1st runnable.
        // Even the previous setTextAsync finishes after the next setTextAsync, the last one should
        // be displayed.
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                // setText may wrap the given text with SpannedString. Check the contents by casting
                // to String.
                assertEquals(SAMPLE_TEXT_2, tv.getText().toString());
                if (Build.VERSION.SDK_INT >= 28) {
                    assertTrue(tv.getText() instanceof PrecomputedText);
                }
            }
        });
    }

    @Test
    public void testSetTextAsync_throwExceptionAfterSetTextFuture() throws Throwable {
        final ManualExecutor executor = new ManualExecutor();
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AppCompatTextView tv = mActivity.findViewById(R.id.textview_set_text_async);
                tv.setText(""); // Make the measured width to be zero.
                tv.setTextFuture(PrecomputedTextCompat.getTextFuture(
                        SAMPLE_TEXT_1, tv.getTextMetricsParamsCompat(), executor));
                tv.setTextSize(tv.getTextSize() * 2.0f + 1.0f);
                executor.doExecution(0);

                // setText may wrap the given text with SpannedString. Check the contents by casting
                // to String.
                try {
                    tv.getText();
                    fail();
                } catch (IllegalArgumentException e) {
                    // pass
                }
            }
        });
    }
}
