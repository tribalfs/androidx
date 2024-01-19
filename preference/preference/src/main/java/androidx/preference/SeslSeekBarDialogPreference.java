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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import androidx.annotation.RestrictTo;

/** @noinspection unused*/
@RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
class SeslSeekBarDialogPreference extends DialogPreference {
    private final Drawable mMyIcon;

    public SeslSeekBarDialogPreference(Context context, AttributeSet attributeSet, int i10, int i11) {
        super(context, attributeSet, i10, i11);
        createActionButtons();
        this.mMyIcon = getDialogIcon();
        setDialogIcon((Drawable) null);
    }

    public static SeekBar getSeekBar(View view) {
        return (SeekBar) view.findViewById(R.id.seekbar);
    }

    public void createActionButtons() {
        setPositiveButtonText(17039370);
        setNegativeButtonText(17039360);
    }

    @Override
    public void onBindDialogView(View view) {
        super.onBindDialogView(view);
        ImageView imageView = (ImageView) view.findViewById(R.id.icon);
        Drawable drawable = this.mMyIcon;
        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        } else {
            imageView.setVisibility(View.GONE);
        }
    }

    public SeslSeekBarDialogPreference(Context context, AttributeSet attributeSet, int i10) {
        this(context, attributeSet, i10, 0);
    }

    public SeslSeekBarDialogPreference(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, R.attr.seekBarDialogPreferenceStyle);
    }

    public SeslSeekBarDialogPreference(Context context) {
        this(context, null);
    }
}