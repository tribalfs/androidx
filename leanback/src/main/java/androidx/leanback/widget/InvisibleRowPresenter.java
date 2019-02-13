/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.leanback.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
public class InvisibleRowPresenter extends RowPresenter {

    public InvisibleRowPresenter() {
        setHeaderPresenter(null);
    }

    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        RelativeLayout root = new RelativeLayout(parent.getContext());
        root.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));
        return new ViewHolder(root);
    }
}
