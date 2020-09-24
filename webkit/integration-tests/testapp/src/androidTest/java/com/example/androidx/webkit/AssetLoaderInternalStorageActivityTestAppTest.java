/*
 * Copyright 2020 The Android Open Source Project
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

package com.example.androidx.webkit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integeration test for AssetLoaderInternalStorageActivity demo activity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class AssetLoaderInternalStorageActivityTestAppTest {

    @Rule
    public IntegrationActivityTestRule<AssetLoaderInternalStorageActivity> mRule =
            new IntegrationActivityTestRule<>(AssetLoaderInternalStorageActivity.class,
                    R.id.webview_asset_loader_webview);

    @Test
    public void testAssetLoaderInternalStorageActivity() {
        mRule.assertHtmlElementContainsText(R.id.webview_asset_loader_webview, "data_success_msg",
                "Successfully loaded html from app files dir!");
    }
}
