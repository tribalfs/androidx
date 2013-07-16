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

package android.support.v4.internal.view;

/**
 * Subclass of {@link SupportMenu} for sub menus.
 *
 * This version extends the one available in the framework to ensures that any necessary
 * elements added in later versions of the framework, are available for all platforms.
 *
 * @see android.view.SubMenu
 * @hide
 */
public interface SupportSubMenu extends SupportMenu, android.view.SubMenu {
}
