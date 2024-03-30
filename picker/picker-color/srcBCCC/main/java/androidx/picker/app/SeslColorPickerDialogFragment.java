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

package androidx.picker.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.util.SeslMisc;
import androidx.picker.R;
import androidx.picker.widget.SeslColorPicker;

import java.io.Serializable;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */
public class SeslColorPickerDialogFragment extends AppCompatDialogFragment
        implements DialogInterface.OnClickListener {
    private static final String TAG = "SeslColorPickerDialogFragment";

    private static final String KEY_CURRENT_COLOR = "current_color";
    private static final String KEY_RECENTLY_USED_COLORS = "recently_used_colors";
    private static final String KEY_OPACITY_BAR_ENABLED = "opacity_bar_enabled";
    private static final String KEY_COLOR_SET_LISTENER = "color_set_listener";

    private AlertDialog mAlertDialog;
    private SeslColorPicker mColorPicker;
    private SeslColorPicker.OnColorChangedListener mOnColorChangedListener;
    private OnColorSetListener mOnColorSetListener;
    private Integer mCurrentColor = null;
    private Integer mNewColor = null;
    private int[] mRecentlyUsedColors = null;
    private boolean mIsTransparencyControlEnabled = false;

    public interface OnColorSetListener extends Serializable {
        void onColorSet(int color);
    }

    public void setOnColorChangedListener(@Nullable SeslColorPicker.OnColorChangedListener listener) {
        mOnColorChangedListener = listener;
    }

    @NonNull
    public static SeslColorPickerDialogFragment newInstance(@Nullable OnColorSetListener listener) {
        SeslColorPickerDialogFragment instance = new SeslColorPickerDialogFragment();

        final Bundle args = new Bundle();
        args.putSerializable(KEY_COLOR_SET_LISTENER, listener);

        instance.setArguments(args);
        return instance;
    }

    @NonNull
    public static SeslColorPickerDialogFragment newInstance(@Nullable OnColorSetListener listener,
                                                            int currentColor) {
        SeslColorPickerDialogFragment instance = new SeslColorPickerDialogFragment();

        final Bundle args = new Bundle();
        args.putSerializable(KEY_COLOR_SET_LISTENER, listener);
        args.putSerializable(KEY_CURRENT_COLOR, currentColor);

        instance.setArguments(args);
        return instance;
    }

    @NonNull
    public static SeslColorPickerDialogFragment newInstance(@Nullable OnColorSetListener onColorSetListener,
                                                            int[] recentlyUsedColors) {
        SeslColorPickerDialogFragment instance = new SeslColorPickerDialogFragment();

        final Bundle args = new Bundle();
        args.putSerializable(KEY_COLOR_SET_LISTENER, onColorSetListener);
        args.putIntArray(KEY_RECENTLY_USED_COLORS, recentlyUsedColors);

        instance.setArguments(args);
        return instance;
    }

    @NonNull
    public static SeslColorPickerDialogFragment newInstance(@Nullable OnColorSetListener onColorSetListener,
                                                            int currentColor,
            int[] recentlyUsedColors) {
        SeslColorPickerDialogFragment instance = new SeslColorPickerDialogFragment();

        final Bundle args = new Bundle();
        args.putSerializable(KEY_COLOR_SET_LISTENER, onColorSetListener);
        args.putSerializable(KEY_CURRENT_COLOR, currentColor);
        args.putIntArray(KEY_RECENTLY_USED_COLORS, recentlyUsedColors);

        instance.setArguments(args);
        return instance;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mRecentlyUsedColors
                    = savedInstanceState.getIntArray(KEY_RECENTLY_USED_COLORS);
            mCurrentColor
                    = (Integer) savedInstanceState.getSerializable(KEY_CURRENT_COLOR);
            mIsTransparencyControlEnabled
                    = savedInstanceState.getBoolean(KEY_OPACITY_BAR_ENABLED);
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mColorPicker = (SeslColorPicker) inflater.inflate(R.layout.sesl_color_picker_dialog, null);

        final Bundle args = getArguments();
        if (args != null) {
            mOnColorSetListener = (OnColorSetListener) args.getSerializable(KEY_COLOR_SET_LISTENER);
            mCurrentColor = (Integer) args.getSerializable(KEY_CURRENT_COLOR);
            mRecentlyUsedColors = args.getIntArray(KEY_RECENTLY_USED_COLORS);
        }

        if (mCurrentColor != null) {
            mColorPicker.getRecentColorInfo().setCurrentColor(mCurrentColor);
        }
        if (mNewColor != null) {
            mColorPicker.getRecentColorInfo().setNewColor(mNewColor);
        }
        if (mRecentlyUsedColors != null) {
            mColorPicker.getRecentColorInfo().initRecentColorInfo(mRecentlyUsedColors);
        }

        mColorPicker.setOpacityBarEnabled(mIsTransparencyControlEnabled);
        mColorPicker.updateRecentColorLayout();
        mColorPicker.setOnColorChangedListener(mOnColorChangedListener);

        mAlertDialog.setView(mColorPicker);

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        mAlertDialog = new ColorPickerDialog(getActivity());
        mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                context.getString(R.string.sesl_picker_done), this);
        mAlertDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                context.getString(R.string.sesl_picker_cancel), this);
        return mAlertDialog;
    }

    @Override
    public void onClick(DialogInterface dialog, int whichButton) {
        switch (whichButton) {
            case DialogInterface.BUTTON_NEGATIVE:
                dialog.dismiss();
                break;
            case DialogInterface.BUTTON_POSITIVE:
                mColorPicker.saveSelectedColor();
                if (mOnColorSetListener != null) {
                    if (mCurrentColor == null || mColorPicker.isUserInputValid()) {
                        mOnColorSetListener.onColorSet(mColorPicker.getRecentColorInfo().getSelectedColor());
                    } else {
                        mOnColorSetListener.onColorSet(mCurrentColor);
                    }
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mColorPicker.getRecentColorInfo()
                .setCurrentColor(mColorPicker.getRecentColorInfo().getSelectedColor());
        outState.putIntArray(KEY_RECENTLY_USED_COLORS, mRecentlyUsedColors);
        outState.putSerializable(KEY_CURRENT_COLOR, mCurrentColor);
        outState.putBoolean(KEY_OPACITY_BAR_ENABLED, mIsTransparencyControlEnabled);
    }

    @NonNull
    public SeslColorPicker getColorPicker() {
        return mColorPicker;
    }

    public void setNewColor(Integer newColor) {
        mNewColor = newColor;
    }

    public void setTransparencyControlEnabled(boolean enabled) {
        mIsTransparencyControlEnabled = enabled;
    }

    private class ColorPickerDialog extends AlertDialog {
        ColorPickerDialog(Context context) {
            super(context,
                    SeslMisc.isLightTheme(context) ?
                            androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light_Dialog :
                            androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog);
        }
    }
}
