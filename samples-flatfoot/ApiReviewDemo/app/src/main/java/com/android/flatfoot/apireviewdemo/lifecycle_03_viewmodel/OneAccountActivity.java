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

package com.android.flatfoot.apireviewdemo.lifecycle_03_viewmodel;

import android.arch.lifecycle.LifecycleActivity;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.flatfoot.apireviewdemo.R;

public class OneAccountActivity extends LifecycleActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_model1);

        AccountViewModel viewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        viewModel.personData.observe(this, new Observer<AccountViewModel.PersonDataWithStatus>() {
            @Override
            public void onChanged(AccountViewModel.PersonDataWithStatus data) {
                if (data.person != null) {
                    TextView emailView = (TextView) findViewById(R.id.name);
                    emailView.setText(data.person.getName());
                    TextView nameView = (TextView) findViewById(R.id.company);
                    nameView.setText(data.person.getCompany());
                }

                findViewById(R.id.loading_spinner).setVisibility(data.loading ?
                        View.VISIBLE : View.GONE);
            }
        });
    }
}
