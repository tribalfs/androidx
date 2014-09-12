/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

class RowContainerViewHeaderDock extends LinearLayout {

    public RowContainerViewHeaderDock(Context context) {
        this(context, null);
    }

    public RowContainerViewHeaderDock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RowContainerViewHeaderDock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Avoid creating hardware layer for header dock.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}