/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.v7.app;

import org.junit.Test;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.support.v7.appcompat.test.R;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.AppCompatSpinner;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LayoutInflaterFactoryTestCase
        extends BaseInstrumentationTestCase<LayoutInflaterFactoryTestActivity> {

    private static final String TINTCONTEXTWRAPPER_CLAZZ_NAME
            = "android.support.v7.widget.TintContextWrapper";

    public LayoutInflaterFactoryTestCase() {
        super(LayoutInflaterFactoryTestActivity.class);
    }

    @Test
    @SmallTest
    public void testAndroidThemeInflation() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LayoutInflater inflater = LayoutInflater.from(getActivity());
                assertThemedContext(inflater,
                        inflater.inflate(R.layout.layout_android_theme, null));
            }
        });
    }

    @Test
    @SmallTest
    public void testAppThemeInflation() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LayoutInflater inflater = LayoutInflater.from(getActivity());
                assertThemedContext(inflater, inflater.inflate(R.layout.layout_app_theme, null));
            }
        });
    }

    @Test
    @SmallTest
    public void testAndroidThemeWithChildrenInflation() throws Throwable {
        if (Build.VERSION.SDK_INT < 11) {
            // Propagation of themed context to children only works on API 11+. Ignoring test.
            return;
        }
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                final ViewGroup root = (ViewGroup) inflater.inflate(
                        R.layout.layout_android_theme_children, null);

                assertThemedContext(inflater, root);

                for (int i = 0; i < root.getChildCount(); i++) {
                    final View child = root.getChildAt(i);
                    assertSame("Child does not have parent's context",
                            root.getContext(), unwrapContextIfNeeded(child.getContext()));
                }
            }
        });
    }

    @Test
    @SmallTest
    public void testSpinnerInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_spinner, AppCompatSpinner.class);
    }

    @Test
    @SmallTest
    public void testEditTextInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_edittext, AppCompatEditText.class);
    }

    @Test
    @SmallTest
    public void testButtonInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_button, AppCompatButton.class);
    }

    @Test
    @SmallTest
    public void testRadioButtonInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_radiobutton, AppCompatRadioButton.class);
    }

    @Test
    @SmallTest
    public void testCheckBoxInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_checkbox, AppCompatCheckBox.class);
    }

    @Test
    @SmallTest
    public void testActvInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_actv, AppCompatAutoCompleteTextView.class);
    }

    @Test
    @SmallTest
    public void testMactvInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_mactv,
                AppCompatMultiAutoCompleteTextView.class);
    }

    @Test
    @SmallTest
    public void testRatingBarInflation() throws Throwable {
        testAppCompatWidgetInflation(R.layout.layout_ratingbar, AppCompatRatingBar.class);
    }

    @Test
    @SmallTest
    public void testDeclarativeOnClickWithContextWrapper() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                View view = inflater.inflate(R.layout.layout_button_themed_onclick, null);

                assertTrue(view.performClick());
                assertTrue(getActivity().wasDeclarativeOnClickCalled());
            }
        });
    }

    private void testAppCompatWidgetInflation(final int layout, final Class<?> expectedClass)
            throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                View view = inflater.inflate(layout, null);
                assertSame("View is " + expectedClass.getSimpleName(), expectedClass,
                        view.getClass());
            }
        });
    }

    private static void assertThemedContext(LayoutInflater inflater, View view) {
        final Context viewContext = unwrapContextIfNeeded(view.getContext());

        assertNotSame("View has same context to LayoutInflater",
                inflater.getContext(), viewContext);
        assertSame("View does not have ContextThemeWrapper context",
                ContextThemeWrapper.class, viewContext.getClass());
    }

    private static Context unwrapContextIfNeeded(Context context) {
        if (TINTCONTEXTWRAPPER_CLAZZ_NAME.equals(context.getClass().getName())) {
            // TintContextWrapper is a special context wrapper used for resource hacking in
            // AppCompat, we'll compare it's base class instead
            return  ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }
}
