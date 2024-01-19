/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.preference;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.reflect.view.SeslViewReflector;
import androidx.reflect.widget.SeslHoverPopupWindowReflector;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/** @noinspection unused*/
public class SeslSwitchPreferenceScreen extends SwitchPreferenceCompat {
    private final View.OnKeyListener mSwitchKeyListener = (view, i, keyEvent) -> {
        int keyCode = keyEvent.getKeyCode();
        if (keyEvent.getAction() != 0) {
            return false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!isChecked()) {
                    return false;
                } else {
                    if (callChangeListener(false)) {
                        setChecked(false);
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isChecked()) {
                    return false;
                } else {
                    if (callChangeListener(true)) {
                        setChecked(true);
                    }
                }
                break;
            default:
                return false;
        }
        return true;
    };

    public SeslSwitchPreferenceScreen(@NonNull Context context) {
        this(context, null);
    }

    public SeslSwitchPreferenceScreen(@NonNull Context context,  @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.switchPreferenceStyle);
    }

    public SeslSwitchPreferenceScreen(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SeslSwitchPreferenceScreen(@NonNull Context context,  @Nullable AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
        super(context, attributeSet, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attributeSet, R.styleable.Preference, defStyleAttr, defStyleRes);
        final Configuration configuration = context.getResources().getConfiguration();
        String fragment = a.getString(R.styleable.Preference_android_fragment);
        if (fragment == null || fragment.equals("")) {
            Log.w("SwitchPreferenceScreen",
                    "SwitchPreferenceScreen should getfragment property. " +
                            "Fragment property does not exist in SwitchPreferenceScreen");
        }
        setLayoutResource(R.layout.sesl_preference_switch_screen);
        setWidgetLayoutResource(R.layout.sesl_switch_preference_screen_widget_divider);
        a.recycle();
    }

    @Override
    public void callClickListener() {
    }

    @Override
    public void onClick() {
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setOnKeyListener(this.mSwitchKeyListener);

        TextView titleView = (TextView) holder.findViewById(android.R.id.title);
        View switchView = holder.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET);
        View switchWidgetView = holder.findViewById(R.id.switch_widget);

        if (titleView == null || switchView == null || switchWidgetView == null) {
            return;
        }
        SeslViewReflector.semSetHoverPopupType(switchView, SeslHoverPopupWindowReflector.getField_TYPE_NONE());
        switchView.setContentDescription(titleView.getText().toString());
        switchWidgetView.setContentDescription(titleView.getText().toString());

    }

}