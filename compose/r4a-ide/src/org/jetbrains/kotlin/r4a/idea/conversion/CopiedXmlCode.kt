/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a.idea.conversion

import com.intellij.codeInsight.editorActions.TextBlockTransferableData

import java.awt.datatransfer.DataFlavor

class CopiedXmlCode(val fileText: String, val startOffsets: IntArray, val endOffsets: IntArray) : TextBlockTransferableData {

    override fun getFlavor() = DATA_FLAVOR
    override fun getOffsetCount() = 0

    override fun getOffsets(offsets: IntArray?, index: Int) = index
    override fun setOffsets(offsets: IntArray?, index: Int) = index

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(CopiedXmlCode::class.java, "Copied XML code")
    }
}