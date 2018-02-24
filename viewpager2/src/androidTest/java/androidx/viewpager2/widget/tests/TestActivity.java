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

package androidx.viewpager2.widget.tests;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import androidx.viewpager2.test.R;
import androidx.viewpager2.widget.ViewPager2;

public class TestActivity extends FragmentActivity {
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_test_layout);

        ViewPager2Tests.sAdapterStrategy.setAdapter((ViewPager2) findViewById(R.id.view_pager));
    }
}
