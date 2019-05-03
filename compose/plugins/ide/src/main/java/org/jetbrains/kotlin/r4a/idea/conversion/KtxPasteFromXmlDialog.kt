/*
 * Copyright 2018 The Android Open Source Project
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

package org.jetbrains.kotlin.r4a.idea.conversion

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.kotlin.r4a.idea.editor.KtxEditorOptions
import java.awt.Container
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class KtxPasteFromXmlDialog(project: Project) : DialogWrapper(project, false) {
    private lateinit var panel: JPanel
    private lateinit var donTShowThisCheckBox: JCheckBox

    init {
        isModal = true
        title = "Convert Code From XML"
        init()
    }

    override fun createCenterPanel(): JComponent = panel

    override fun getContentPane(): Container = panel

    override fun createActions(): Array<Action> {
        setOKButtonText(CommonBundle.getYesButtonText())
        setCancelButtonText(CommonBundle.getNoButtonText())
        return arrayOf(okAction, cancelAction)
    }

    override fun doOKAction() {
        if (donTShowThisCheckBox.isSelected) {
            KtxEditorOptions.getInstance().donTShowKtxConversionDialog = true
        }
        super.doOKAction()
    }
}