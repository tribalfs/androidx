/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.autofill

import android.graphics.Matrix
import android.graphics.Rect
import android.os.Bundle
import android.os.LocaleList
import android.util.SparseArray
import android.view.View
import android.view.ViewStructure
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import com.nhaarman.mockitokotlin2.mock

/**
 * A fake implementation of { SparseArray } to use in tests.
 */
internal class FakeSparseArray : SparseArray<AutofillValue>() {

    private val map = mutableMapOf<Int, AutofillValue>()

    override fun append(key: Int, value: AutofillValue?) {
        value?.let { map.putIfAbsent(key, it) }
    }

    override fun size() = map.count()

    override fun keyAt(index: Int) = map.asSequence().elementAt(index).component1()

    override fun get(key: Int) = map[key] ?: error("no element for the specified key")
}

/**
 * A fake implementation of { ViewStructure } to use in tests.
 */
internal data class FakeViewStructure(
    var virtualId: Int = 0,
    var packageName: String? = null,
    var typeName: String? = null,
    var entryName: String? = null,
    var children: MutableList<FakeViewStructure> = mutableListOf(),
    var bounds: Rect? = null,
    private val autofillId: AutofillId? = mock(),
    private var autofillType: Int = View.AUTOFILL_TYPE_NONE,
    private var autofillHints: Array<out String> = arrayOf()
) : ViewStructure() {

    override fun getChildCount() = children.count()

    override fun addChildCount(childCount: Int): Int {
        repeat(childCount) { children.add(FakeViewStructure()) }
        return children.count() - childCount
    }

    override fun newChild(index: Int): FakeViewStructure {
        if (index >= children.count()) error("Call addChildCount() before calling newChild()")
        return children[index]
    }

    override fun getAutofillId() = autofillId

    override fun setAutofillId(rootId: AutofillId, virtualId: Int) {
        this.virtualId = virtualId
    }

    override fun setId(
        virtualId: Int,
        packageName: String?,
        typeName: String?,
        entryName: String?
    ) {
        this.virtualId = virtualId
        this.packageName = packageName
        this.typeName = typeName
        this.entryName = entryName
    }

    override fun setAutofillType(autofillType: Int) {
        this.autofillType = autofillType
    }

    override fun setAutofillHints(autofillHints: Array<out String>?) {
        autofillHints?.let { this.autofillHints = it }
    }

    override fun setDimens(left: Int, top: Int, x: Int, y: Int, width: Int, height: Int) {
        this.bounds = Rect(left, top, width - left, height - top)
    }

    override fun equals(other: Any?) = other is FakeViewStructure &&
            other.virtualId == virtualId &&
            other.packageName == packageName &&
            other.typeName == typeName &&
            other.entryName == entryName &&
            other.autofillType == autofillType &&
            other.autofillHints.contentEquals(autofillHints) &&
            other.bounds.contentEquals(bounds) &&
            other.children == children

    override fun hashCode() = super.hashCode()

    // Unimplemented methods.

    override fun setOpaque(p0: Boolean) { TODO("not implemented") }

    override fun setHint(p0: CharSequence?) { TODO("not implemented") }

    override fun setElevation(p0: Float) { TODO("not implemented") }

    override fun getText(): CharSequence { TODO("not implemented") }

    override fun setText(p0: CharSequence?) { TODO("not implemented") }

    override fun setText(p0: CharSequence?, p1: Int, p2: Int) { TODO("not implemented") }

    override fun asyncCommit() { TODO("not implemented") }

    override fun setEnabled(p0: Boolean) { TODO("not implemented") }

    override fun setLocaleList(p0: LocaleList?) { TODO("not implemented") }

    override fun setChecked(p0: Boolean) { TODO("not implemented") }

    override fun setContextClickable(p0: Boolean) { TODO("not implemented") }

    override fun setAccessibilityFocused(p0: Boolean) { TODO("not implemented") }

    override fun setAlpha(p0: Float) { TODO("not implemented") }

    override fun setTransformation(p0: Matrix?) { TODO("not implemented") }

    override fun setClassName(p0: String?) { TODO("not implemented") }

    override fun setLongClickable(p0: Boolean) { TODO("not implemented") }

    override fun getHint(): CharSequence { TODO("not implemented") }

    override fun setInputType(p0: Int) { TODO("not implemented") }

    override fun setWebDomain(p0: String?) { TODO("not implemented") }

    override fun setAutofillOptions(p0: Array<out CharSequence>?) { TODO("not implemented") }

    override fun setTextStyle(p0: Float, p1: Int, p2: Int, p3: Int) { TODO("not implemented") }

    override fun setVisibility(p0: Int) { TODO("not implemented") }

    override fun setHtmlInfo(p0: HtmlInfo) { TODO("not implemented") }

    override fun setTextLines(p0: IntArray?, p1: IntArray?) { TODO("not implemented") }

    override fun getExtras(): Bundle { TODO("not implemented") }

    override fun setClickable(p0: Boolean) { TODO("not implemented") }

    override fun newHtmlInfoBuilder(p0: String): HtmlInfo.Builder { TODO("not implemented") }

    override fun getTextSelectionEnd(): Int { TODO("not implemented") }

    override fun setAutofillId(p0: AutofillId) { TODO("not implemented") }

    override fun hasExtras(): Boolean { TODO("not implemented") }

    override fun setActivated(p0: Boolean) { TODO("not implemented") }

    override fun setFocused(p0: Boolean) { TODO("not implemented") }

    override fun getTextSelectionStart(): Int { TODO("not implemented") }

    override fun setChildCount(p0: Int) { TODO("not implemented") }

    override fun setAutofillValue(p0: AutofillValue?) { TODO("not implemented") }

    override fun setContentDescription(p0: CharSequence?) { TODO("not implemented") }

    override fun setFocusable(p0: Boolean) { TODO("not implemented") }

    override fun setCheckable(p0: Boolean) { TODO("not implemented") }

    override fun asyncNewChild(p0: Int): ViewStructure { TODO("not implemented") }

    override fun setSelected(p0: Boolean) { TODO("not implemented") }

    override fun setDataIsSensitive(p0: Boolean) { TODO("not implemented") }
}

private fun Rect?.contentEquals(other: Rect?) = when {
    (other == null && this == null) -> true
    (other == null || this == null) -> false
    else -> other.left == left &&
            other.right == right &&
            other.bottom == bottom &&
            other.top == top
}