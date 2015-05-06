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

package com.example.android.support.design.widget;

import com.example.android.support.design.Cheeses;
import com.example.android.support.design.R;
import com.example.android.support.design.Shakespeare;

import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.widget.TextView;

import java.util.Random;

abstract class AppBarLayoutUsageBase extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());

        // Retrieve the Toolbar from our content view, and set it as the action bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout)
                findViewById(R.id.collapsing_app_bar);
        if (appBarLayout != null && displayTitle()) {
            appBarLayout.setTitle(getTitle());
        }

        TextView dialog = (TextView) findViewById(R.id.textview_dialogue);
        if (dialog != null) {
            dialog.setText(TextUtils.concat(Shakespeare.DIALOGUE));
        }

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.appbar_recyclerview);
        if (recyclerView != null) {
            setupRecyclerView(recyclerView);
        }

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        if (tabLayout != null) {
            setupTabs(tabLayout);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sample_actions, menu);
        return true;
    }

    private void addRandomTab(TabLayout tabLayout) {
        Random r = new Random();
        String cheese = Cheeses.sCheeseStrings[r.nextInt(Cheeses.sCheeseStrings.length)];
        tabLayout.addTab(tabLayout.newTab().setText(cheese));
    }

    private void setupTabs(TabLayout tabLayout) {
        for (int i = 0; i < 10; i++) {
            addRandomTab(tabLayout);
        }
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new SimpleStringRecyclerViewAdapter(this, Cheeses.sCheeseStrings));
    }

    protected boolean displayTitle() {
        return true;
    }

    protected abstract int getLayoutId();

}
