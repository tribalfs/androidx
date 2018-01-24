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

package android.support.v4.widget;

import static org.junit.Assert.assertEquals;

import android.support.coreui.test.R;
import android.support.test.filters.LargeTest;
import android.support.testutils.PollingCheck;
import android.support.v4.BaseInstrumentationTestCase;
import android.view.View;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ContentLoadingProgressBar}
 */
public class ContentLoadingProgressBarTest extends
        BaseInstrumentationTestCase<ContentLoadingProgressBarActivity> {

    public ContentLoadingProgressBarTest() {
        super(ContentLoadingProgressBarActivity.class);
    }

    private ContentLoadingProgressBar mContentLoadingProgressBar;

    @Before
    public void setUp() {
        mContentLoadingProgressBar = mActivityTestRule.getActivity().findViewById(R.id.progressBar);
    }

    @Test
    @LargeTest
    public void showAndThenLaterHide() {
        mContentLoadingProgressBar.show();

        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mContentLoadingProgressBar.getVisibility() == View.VISIBLE;
            }
        });

        mContentLoadingProgressBar.hide();

        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mContentLoadingProgressBar.getVisibility() == View.GONE;
            }
        });
    }

    @Test
    @LargeTest
    public void showAndImmediatelyHide() {
        mContentLoadingProgressBar.show();
        mContentLoadingProgressBar.hide();

        // show() followed immediately by hide() should leave the progress bar in GONE state
        assertEquals(mContentLoadingProgressBar.getVisibility(), View.GONE);

        // The next show() should eventually show the progress bar
        mContentLoadingProgressBar.show();
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mContentLoadingProgressBar.getVisibility() == View.VISIBLE;
            }
        });


        // The next hide() should eventually hide the progress bar
        mContentLoadingProgressBar.hide();
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mContentLoadingProgressBar.getVisibility() == View.GONE;
            }
        });
    }
}
