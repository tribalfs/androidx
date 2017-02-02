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
package android.support.v17.leanback.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.test.R;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

public class SingleSupportFragmentTestActivity extends android.support.v4.app.FragmentActivity {

    /**
     * Fragment that will be added to activity
     */
    public static final String EXTRA_FRAGMENT_NAME = "fragmentName";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        setContentView(R.layout.rows);
        if (savedInstanceState == null) {
            try {
                Fragment fragment = (Fragment) Class.forName(
                        intent.getStringExtra(EXTRA_FRAGMENT_NAME)).newInstance();
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.main_frame, fragment);
                ft.commit();
            } catch (Exception ex) {
                ex.printStackTrace();
                finish();
            }
        }
    }

    public Fragment getTestFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.main_frame);
    }
}
