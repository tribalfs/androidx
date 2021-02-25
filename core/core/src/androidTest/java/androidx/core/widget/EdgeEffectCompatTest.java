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

package androidx.core.widget;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.v4.BaseInstrumentationTestCase;
import android.support.v4.BaseTestActivity;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EdgeEffect;

import androidx.core.test.R;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EdgeEffectCompatTest extends
        BaseInstrumentationTestCase<EdgeEffectCompatTest.EdgeEffectCompatTestActivity> {
    private ViewWithEdgeEffect mView;
    private EdgeEffect mEdgeEffect;

    public EdgeEffectCompatTest() {
        super(EdgeEffectCompatTestActivity.class);
    }

    @Before
    public void setUp() {
        Activity activity = mActivityTestRule.getActivity();
        mView = activity.findViewById(R.id.edgeEffectView);
        mEdgeEffect = mView.mEdgeEffect;
    }
    // TODO(b/181171227): Change to R
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    @Test
    public void edgeEffectTypeApi30() {
        assertEquals(EdgeEffect.TYPE_GLOW, EdgeEffectCompat.getType(mEdgeEffect));
        EdgeEffectCompat.setType(mEdgeEffect, EdgeEffect.TYPE_STRETCH);
        // nothing should change for R and earlier
        assertEquals(EdgeEffect.TYPE_GLOW, EdgeEffectCompat.getType(mEdgeEffect));
    }

    // TODO(b/181171227): Change to S
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void edgeEffectTypeApi31() {
        // TODO(b/181171227): Remove this condition
        if (isSOrHigher()) {
            assertEquals(EdgeEffect.TYPE_STRETCH, EdgeEffectCompat.getType(mEdgeEffect));
            EdgeEffectCompat.setType(mEdgeEffect, EdgeEffect.TYPE_GLOW);
            assertEquals(EdgeEffect.TYPE_GLOW, EdgeEffectCompat.getType(mEdgeEffect));
        } else {
            edgeEffectTypeApi30();
        }
    }

    // TODO(b/181171227): Change to R
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    @Test
    public void distanceApi30() {
        assertEquals(0, EdgeEffectCompat.getDistance(mEdgeEffect), 0f);
        assertEquals(1f, EdgeEffectCompat.onPullDistance(mEdgeEffect, 1, 0.5f), 0f);
        assertEquals(0, EdgeEffectCompat.getDistance(mEdgeEffect), 0f);
    }

    // TODO(b/181171227): Change to S
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void distanceApi31() {
        // TODO(b/181171227): Remove this condition
        if (isSOrHigher()) {
            assertEquals(0, EdgeEffectCompat.getDistance(mEdgeEffect), 0f);
            assertEquals(1f, EdgeEffectCompat.onPullDistance(mEdgeEffect, 1, 0.5f), 0f);
            assertEquals(1, EdgeEffectCompat.getDistance(mEdgeEffect), 0f);
            assertEquals(-1f, EdgeEffectCompat.onPullDistance(mEdgeEffect, -1.5f, 0.5f), 0f);
            assertEquals(0, EdgeEffectCompat.getDistance(mEdgeEffect), 0f);
        } else {
            distanceApi30();
        }
    }

    // TODO(b/181171227): Remove this.
    private static boolean isSOrHigher() {
        int sdk = Build.VERSION.SDK_INT;
        return sdk > Build.VERSION_CODES.R
                || (sdk == Build.VERSION_CODES.R && Build.VERSION.PREVIEW_SDK_INT != 0);
    }

    public static class EdgeEffectCompatTestActivity extends BaseTestActivity {
        @Override
        protected int getContentViewLayoutResId() {
            return R.layout.edge_effect_compat;
        }
    }

    public static class ViewWithEdgeEffect extends View {
        public EdgeEffect mEdgeEffect;

        public ViewWithEdgeEffect(Context context) {
            this(context, null);
        }

        public ViewWithEdgeEffect(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public ViewWithEdgeEffect(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public ViewWithEdgeEffect(Context context, AttributeSet attrs, int defStyleAttr,
                int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);

            mEdgeEffect = EdgeEffectCompat.create(context, attrs);
        }
    }
}
