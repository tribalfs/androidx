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

package android.support.design.widget;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Modal bottom sheet. This is a version of {@link DialogFragment} that shows a bottom sheet
 * using {@link BottomSheetDialog} instead of a floating dialog.
 */
public class BottomSheetDialogFragment extends AppCompatDialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new BottomSheetDialog(getActivity(), getTheme());
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return dialog;
    }

    /** @hide */
    @Override
    public void setupDialog(Dialog dialog, int style) {
        if (dialog instanceof BottomSheetDialog) {
            // We hide the title bar for any style configuration. Otherwise, there will be a gap
            // above the bottom sheet when it is expanded.
            ((BottomSheetDialog) dialog).supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            super.setupDialog(dialog, style);
        }
    }

}
