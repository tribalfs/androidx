/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.support.v7.internal.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v7.internal.view.menu.MenuWrapperFactory;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

/**
 * Wraps a support {@link android.support.v7.view.ActionMode} as a framework
 * {@link android.view.ActionMode}.
 *
 * @hide
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SupportActionModeWrapper extends ActionMode {

    final MenuInflater mInflater;
    final android.support.v7.view.ActionMode mWrappedObject;

    public SupportActionModeWrapper(Context context, android.support.v7.view.ActionMode supportActionMode) {
        mWrappedObject = supportActionMode;
        mInflater = new SupportMenuInflater(context);
    }

    @Override
    public Object getTag() {
        return mWrappedObject.getTag();
    }

    @Override
    public void setTag(Object tag) {
        mWrappedObject.setTag(tag);
    }

    @Override
    public void setTitle(CharSequence title) {
        mWrappedObject.setTitle(title);
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
        mWrappedObject.setSubtitle(subtitle);
    }

    @Override
    public void invalidate() {
        mWrappedObject.invalidate();
    }

    @Override
    public void finish() {
        mWrappedObject.finish();
    }

    @Override
    public Menu getMenu() {
        return MenuWrapperFactory.createMenuWrapper(mWrappedObject.getMenu());
    }

    @Override
    public CharSequence getTitle() {
        return mWrappedObject.getTitle();
    }

    @Override
    public void setTitle(int resId) {
        mWrappedObject.setTitle(resId);
    }

    @Override
    public CharSequence getSubtitle() {
        return mWrappedObject.getSubtitle();
    }

    @Override
    public void setSubtitle(int resId) {
        mWrappedObject.setSubtitle(resId);
    }

    @Override
    public View getCustomView() {
        return mWrappedObject.getCustomView();
    }

    @Override
    public void setCustomView(View view) {
        mWrappedObject.setCustomView(view);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return mInflater;
    }

    @Override
    public boolean getTitleOptionalHint() {
        return mWrappedObject.getTitleOptionalHint();
    }

    @Override
    public void setTitleOptionalHint(boolean titleOptional) {
        mWrappedObject.setTitleOptionalHint(titleOptional);
    }

    @Override
    public boolean isTitleOptional() {
        return mWrappedObject.isTitleOptional();
    }

    /**
     * @hide
     */
    public static class CallbackWrapper implements android.support.v7.view.ActionMode.Callback {
        final Callback mWrappedCallback;
        final Context mContext;

        private SupportActionModeWrapper mLastStartedActionMode;

        public CallbackWrapper(Context context, Callback supportCallback) {
            mContext = context;
            mWrappedCallback = supportCallback;
        }

        @Override
        public boolean onCreateActionMode(android.support.v7.view.ActionMode mode, Menu menu) {
            return mWrappedCallback.onCreateActionMode(getActionModeWrapper(mode),
                    MenuWrapperFactory.createMenuWrapper(menu));
        }

        @Override
        public boolean onPrepareActionMode(android.support.v7.view.ActionMode mode, Menu menu) {
            return mWrappedCallback.onPrepareActionMode(getActionModeWrapper(mode),
                    MenuWrapperFactory.createMenuWrapper(menu));
        }

        @Override
        public boolean onActionItemClicked(android.support.v7.view.ActionMode mode,
                android.view.MenuItem item) {
            return mWrappedCallback.onActionItemClicked(getActionModeWrapper(mode),
                    MenuWrapperFactory.createMenuItemWrapper(item));
        }

        @Override
        public void onDestroyActionMode(android.support.v7.view.ActionMode mode) {
            mWrappedCallback.onDestroyActionMode(getActionModeWrapper(mode));
        }

        private ActionMode getActionModeWrapper(android.support.v7.view.ActionMode mode) {
            if (mLastStartedActionMode != null && mLastStartedActionMode.mWrappedObject == mode) {
                // If the given mode equals our wrapped mode, just return it
                return mLastStartedActionMode;
            } else {
                mLastStartedActionMode = new SupportActionModeWrapper(mContext, mode);
                return mLastStartedActionMode;
            }
        }
    }
}
