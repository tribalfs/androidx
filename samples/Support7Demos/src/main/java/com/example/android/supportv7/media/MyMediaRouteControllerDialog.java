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

package com.example.android.supportv7.media;

import android.graphics.Color;
import android.os.Bundle;
import androidx.mediarouter.app.MediaRouteControllerDialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.example.android.supportv7.R;

/**
 * An example MediaRouteControllerDialog for demonstrating
 * {@link androidx.mediarouter.app.MediaRouteControllerDialog#onCreateMediaControlView}.
 */
public class MyMediaRouteControllerDialog extends MediaRouteControllerDialog {
    public MyMediaRouteControllerDialog(Context context) {
        super(context);
    }

    @Override
    public View onCreateMediaControlView(Bundle savedInstanceState) {
        TextView view = new TextView(getContext());
        view.setText(R.string.my_media_control_text);
        view.setBackgroundColor(Color.GRAY);
        return view;
    }
}
