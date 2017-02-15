// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from RowsFragmentTestActivity.java.  DO NOT MODIFY. */

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

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.test.R;

public class RowsSupportFragmentTestActivity extends FragmentActivity {

    public static final String EXTRA_NUM_ROWS = "numRows";
    public static final String EXTRA_REPEAT_PER_ROW = "repeatPerRow";
    public static final String EXTRA_LOAD_DATA_DELAY = "loadDataDelay";
    public final static String EXTRA_SET_ADAPTER_AFTER_DATA_LOAD = "set_adapter_after_data_load";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        setContentView(R.layout.rows);
        if (savedInstanceState == null) {
            RowsTestSupportFragment fragment = new RowsTestSupportFragment();
            Bundle arguments = new Bundle();
            if (intent.getExtras() != null) {
                arguments.putAll(intent.getExtras());
            }
            fragment.setArguments(arguments);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.main_frame, fragment);
            ft.commit();
        }
    }

    public RowsTestSupportFragment getRowsTestSupportFragment() {
        return (RowsTestSupportFragment) getSupportFragmentManager().findFragmentById(R.id.main_frame);
    }
}
