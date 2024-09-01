/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appcompat.view.menu;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

import androidx.annotation.Nullable;

public interface SeslMenuItem {

    /**
     *
     * @return The badge text of this menu item.
     * @see #setBadgeText(String)
     */
    @Nullable
    String getBadgeText();

    /**
     * Sets a badge to this menu item.
     * @param badgeText  Set a numeric string to show a numeric badge.
     *                   Set an empty string to show a dot badge.
     *                   Set null to remove the badge.
     * @see #getBadgeText()
     */
    void setBadgeText(@Nullable String badgeText);
}
