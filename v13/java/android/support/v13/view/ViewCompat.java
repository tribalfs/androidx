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

package android.support.v13.view;

import android.content.ClipData;
import android.support.annotation.RequiresApi;
import android.support.v4.os.BuildCompat;
import android.view.View;

/**
 * Helper for accessing features in {@link View} introduced after API
 * level 13 in a backwards compatible fashion.
 */
public class ViewCompat extends android.support.v4.view.ViewCompat {
    interface ViewCompatImpl {
        boolean startDragAndDrop(View v, ClipData data, View.DragShadowBuilder shadowBuilder,
                Object localState, int flags);
        void cancelDragAndDrop(View v);
        void updateDragShadow(View v, View.DragShadowBuilder shadowBuilder);
    }

    private static class ViewCompatBaseImpl implements ViewCompatImpl {
        ViewCompatBaseImpl() {
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean startDragAndDrop(View v, ClipData data, View.DragShadowBuilder shadowBuilder,
                Object localState, int flags) {
            return v.startDrag(data, shadowBuilder, localState, flags);
        }

        @Override
        public void cancelDragAndDrop(View v) {
            // no-op
        }

        @Override
        public void updateDragShadow(View v, View.DragShadowBuilder shadowBuilder) {
            // no-op
        }
    }

    @RequiresApi(24)
    private static class ViewCompatApi24Impl implements ViewCompatImpl {
        ViewCompatApi24Impl() {}

        @Override
        public boolean startDragAndDrop(View view, ClipData data,
                View.DragShadowBuilder shadowBuilder, Object localState, int flags) {
            return view.startDragAndDrop(data, shadowBuilder, localState, flags);
        }

        @Override
        public void cancelDragAndDrop(View view) {
            view.cancelDragAndDrop();
        }

        @Override
        public void updateDragShadow(View view, View.DragShadowBuilder shadowBuilder) {
            view.updateDragShadow(shadowBuilder);
        }
    }

    static ViewCompatImpl IMPL;
    static {
        if (BuildCompat.isAtLeastN()) {
            IMPL = new ViewCompatApi24Impl();
        } else {
            IMPL = new ViewCompatBaseImpl();
        }
    }

    /**
     * Start the drag and drop operation.
     */
    public static boolean startDragAndDrop(View v, ClipData data,
            View.DragShadowBuilder shadowBuilder, Object localState, int flags) {
        return IMPL.startDragAndDrop(v, data, shadowBuilder, localState, flags);
    }

    /**
     * Cancel the drag and drop operation.
     */
    public static void cancelDragAndDrop(View v) {
        IMPL.cancelDragAndDrop(v);
    }

    /**
     * Update the drag shadow while drag and drop is in progress.
     */
    public static void updateDragShadow(View v, View.DragShadowBuilder shadowBuilder) {
        IMPL.updateDragShadow(v, shadowBuilder);
    }

    private ViewCompat() {
    }
}
