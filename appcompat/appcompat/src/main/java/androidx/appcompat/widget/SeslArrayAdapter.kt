/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.appcompat.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import androidx.appcompat.R

//Added in sesl7
class SeslArrayAdapter(context: Context, @LayoutRes resource: Int) :
    ArrayAdapter<Any?>(context, resource) {
    private var mInitPaddingBottom = 0
    private var mInitPaddingTop = 0

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val dropDownView = super.getDropDownView(position, convertView, parent)
        if (convertView == null) {
            mInitPaddingTop = dropDownView.paddingTop
            mInitPaddingBottom = dropDownView.paddingBottom
        }
        val firstLastItemVerticalEdgePadding = dropDownView.resources.getDimensionPixelSize(R.dimen.sesl_popup_menu_first_last_item_vertical_edge_padding)
        var paddingTop = mInitPaddingTop + firstLastItemVerticalEdgePadding
        var paddingBottom = mInitPaddingBottom + firstLastItemVerticalEdgePadding
        val paddingLeft = dropDownView.paddingLeft
        val paddingRight = dropDownView.paddingRight
        if (position != 0) {
            paddingTop = mInitPaddingTop
        }
        if (position != count - 1) {
            paddingBottom = mInitPaddingBottom
        }
        dropDownView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        return dropDownView
    }
}