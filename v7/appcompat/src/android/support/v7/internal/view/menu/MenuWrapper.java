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

import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;

public class MenuWrapper {

    public static Menu createMenuWrapper(android.view.Menu frameworkMenu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return new MenuWrapperHC(frameworkMenu);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new MenuWrapperICS(frameworkMenu);
        }

        return frameworkMenu;
    }

    public static MenuItem createMenuItemWrapper(android.view.MenuItem frameworkMenuItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return new MenuItemWrapperHC(frameworkMenuItem);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new MenuItemWrapperICS(frameworkMenuItem);
        }

        return frameworkMenuItem;
    }

}
