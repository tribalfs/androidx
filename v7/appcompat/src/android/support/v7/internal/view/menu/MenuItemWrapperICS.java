/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.support.v7.internal.view.menu;

import android.support.v4.view.ActionProvider;
import android.support.v4.internal.view.SupportMenuItem;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

class MenuItemWrapperICS extends MenuItemWrapperHC {

    MenuItemWrapperICS(android.view.MenuItem object) {
        super(object);
    }

    @Override
    public MenuItem setActionProvider(android.view.ActionProvider provider) {
        mWrappedObject.setActionProvider(provider);
        return this;
    }

    @Override
    public android.view.ActionProvider getActionProvider() {
        return mWrappedObject.getActionProvider();
    }

    @Override
    public SupportMenuItem setSupportActionProvider(ActionProvider actionProvider) {
        mWrappedObject.setActionProvider(new ActionProviderWrapper(actionProvider));
        return this;
    }

    @Override
    public ActionProvider getSupportActionProvider() {
        return ((ActionProviderWrapper) mWrappedObject.getActionProvider()).getWrappedObject();
    }

    @Override
    public boolean expandActionView() {
        return mWrappedObject.expandActionView();
    }

    @Override
    public boolean collapseActionView() {
        return mWrappedObject.collapseActionView();
    }

    @Override
    public boolean isActionViewExpanded() {
        return mWrappedObject.isActionViewExpanded();
    }

    @Override
    public MenuItem setOnActionExpandListener(MenuItem.OnActionExpandListener listener) {
        mWrappedObject.setOnActionExpandListener(new OnActionExpandListenerWrapper(listener));
        return this;
    }

    @Override
    SupportMenuItem createMenuItemWrapper(MenuItem menuItem) {
        return new MenuItemWrapperICS(menuItem);
    }

    @Override
    SubMenu createSubMenuWrapper(android.view.SubMenu subMenu) {
        return new SubMenuWrapperICS(subMenu);
    }

    private class OnActionExpandListenerWrapper extends BaseWrapper<MenuItem.OnActionExpandListener>
            implements android.view.MenuItem.OnActionExpandListener {

        OnActionExpandListenerWrapper(MenuItem.OnActionExpandListener object) {
            super(object);
        }

        @Override
        public boolean onMenuItemActionExpand(android.view.MenuItem item) {
            return mWrappedObject.onMenuItemActionExpand(getMenuItemWrapper(item));
        }

        @Override
        public boolean onMenuItemActionCollapse(android.view.MenuItem item) {
            return mWrappedObject.onMenuItemActionCollapse(getMenuItemWrapper(item));
        }
    }

    class ActionProviderWrapper extends android.view.ActionProvider {

        final ActionProvider mWrappedObject;

        public ActionProviderWrapper(ActionProvider object) {
            super(null);
            mWrappedObject = object;
        }

        @Override
        public View onCreateActionView() {
            return mWrappedObject.onCreateActionView(MenuItemWrapperICS.this.mWrappedObject);
        }

        public boolean overridesItemVisibility() {
            return mWrappedObject.overridesItemVisibility();
        }

        public boolean isVisible() {
            return mWrappedObject.isVisible();
        }

        public void refreshVisibility() {
            mWrappedObject.refreshVisibility();
        }


        @Override
        public boolean onPerformDefaultAction() {
            return mWrappedObject.onPerformDefaultAction();
        }

        @Override
        public boolean hasSubMenu() {
            return mWrappedObject.hasSubMenu();
        }

        @Override
        public void onPrepareSubMenu(android.view.SubMenu subMenu) {
            mWrappedObject.onPrepareSubMenu(getSubMenuWrapper(subMenu));
        }

        ActionProvider getWrappedObject() {
            return mWrappedObject;
        }
    }

}
