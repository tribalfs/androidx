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
package android.support.v17.leanback.widget;

import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @hide
 */
public class PageRowPresenter extends RowPresenter {

    public PageRowPresenter() {
        setHeaderPresenter(null);
    }

    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        RelativeLayout root = new RelativeLayout(parent.getContext());
        root.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));
        return new ViewHolder(root);
    }
}
