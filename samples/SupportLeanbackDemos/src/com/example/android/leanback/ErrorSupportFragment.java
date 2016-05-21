/* This file is auto-generated from ErrorFragment.java.  DO NOT MODIFY. */

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.android.leanback;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.SearchOrbView;
import android.util.Log;
import android.view.View;

public class ErrorSupportFragment extends android.support.v17.leanback.app.ErrorSupportFragment {
    private static final String TAG = "leanback.ErrorSupportFragment";
    private static final boolean TRANSLUCENT = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setTitle("Leanback Sample App");
    }

    void setErrorContent(Resources resources) {
        setImageDrawable(resources.getDrawable(R.drawable.lb_ic_sad_cloud));
        setMessage("An error occurred.");
        setDefaultBackground(TRANSLUCENT);

        setButtonText("Dismiss");
        setButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.i(TAG, "button clicked");
                getFragmentManager().beginTransaction().remove(ErrorSupportFragment.this).commit();
            }
        });
    }
}
