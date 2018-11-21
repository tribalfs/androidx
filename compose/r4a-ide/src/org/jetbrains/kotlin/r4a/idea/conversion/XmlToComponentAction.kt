/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a.idea.conversion

import com.google.common.base.CaseFormat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.refactoring.toPsiFile
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.EmptyDocCommentConverter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class XmlToComponentAction : AnAction() {
    companion object {
        fun convertFiles(xmlFiles: List<XmlFile>) {
            if (xmlFiles.isEmpty()) {
                return
            }

            // TODO(jdemeulenaere): Check if there are syntax errors in any file, in which case either abort or warn that result might be incorrect.

            val project = xmlFiles.first().project
            project.executeWriteCommand("Convert XML Files to Component") {
                val convertedFiles = xmlFiles.map { convertFile(it) }
                PsiDocumentManager.getInstance(project).commitAllDocuments()

                // Open first converted file.
                FileEditorManager.getInstance(project).openFile(convertedFiles.first().virtualFile, true)
            }
        }

        private fun convertFile(sourceFile: XmlFile): KtFile {
            val copy = sourceFile.copy() as XmlFile
            val targetFile = renameFile(sourceFile)

            val convertedXml = XmlToKtxConverter(targetFile).convertElement(copy)
            // TODO(jdemeulenaere): Better comment converter.
            val codeBuilder = CodeBuilder(null, EmptyDocCommentConverter)
            codeBuilder.append(convertedXml)
            val functionName = functionName(copy)
            val content = createFunctionalComponent(functionName, codeBuilder.resultText)

            replaceContent(targetFile, content)
            addImports(targetFile, codeBuilder.importsToAdd)
            XmlToKtxConverter.formatCode(targetFile)
            return targetFile
        }

        private fun functionName(file: XmlFile): String {
            // TODO(jdemeulenaere): Ask the user which file/function name to use (and pre-fill name with this algorithm) if converting only one file.
            var functionName = file.name

            // Remove extension.
            val dotIndex = functionName.lastIndexOf('.')
            if (dotIndex != -1) {
                functionName = functionName.substring(0, dotIndex)
            }

            // Remove special characters.
            functionName = functionName.replace(Regex("[^A-Za-z0-9_]"), "")

            // Strip activity_* prefix away.
            if (functionName.startsWith("activity_")) {
                functionName = functionName.substring("activity_".length)
            }

            // Convert case.
            return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, functionName)
        }

        private fun createFunctionalComponent(name: String, composeBody: String): String {
            // TODO(jdemeulenaere): Infer package from file location/siblings/parents.
            // We don't use org.jetbrains.kotlin.j2k.ast.Function because it requires a j2k.Converter instance to create a DeferredElement
            // (the type of the Function body).

            return """
            |import com.google.r4a.*
            |
            |@Composable
            |fun $name() {
            |    $composeBody
            |}""".trimMargin()
        }

        private fun replaceContent(file: KtFile, content: String) {
            // Replace content.
            val documentManager = PsiDocumentManager.getInstance(file.project)
            val document = documentManager.getDocument(file)!!
            document.replaceString(0, document.textLength, content)
            FileDocumentManager.getInstance().saveDocument(document)
            documentManager.commitDocument(document)
        }

        private fun renameFile(sourceFile: XmlFile): KtFile {
            // TODO(jdemeulenaere): If we are converting only one file, ask the user in which folder we should move that file.
            // TODO(jdemeulenaere): Handle scratch files (change language mapping).
            val virtualFile = sourceFile.virtualFile
            val ioFile = File(virtualFile.path.replace('/', File.separatorChar))
            val className = functionName(sourceFile)
            var kotlinFileName = "$className.kt"
            var i = 1
            while (true) {
                if (!ioFile.resolveSibling(kotlinFileName).exists()) break
                kotlinFileName = "$className${i++}.kt"
            }
            virtualFile.rename(this, kotlinFileName)
            return virtualFile.toPsiFile(sourceFile.project)!! as KtFile
        }

        private fun addImports(ktFile: KtFile, imports: Collection<FqName>) {
            runWriteAction {
                imports.forEach { fqName ->
                    ktFile.resolveImportReference(fqName).firstOrNull()?.let {
                        ImportInsertHelper.getInstance(ktFile.project).importDescriptor(ktFile, it)
                    }
                }
            }
        }

        private fun isAnyXmlFileSelected(project: Project, files: Array<VirtualFile>): Boolean {
            val manager = PsiManager.getInstance(project)

            if (files.any { manager.findFile(it) is XmlFile && it.isWritable }) return true
            return files.any { it.isDirectory && isAnyXmlFileSelected(project, it.children) }
        }

        private fun selectedXmlFiles(event: AnActionEvent): Sequence<XmlFile> {
            val virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return sequenceOf()
            val project = event.project ?: return sequenceOf()
            return allXmlFiles(virtualFiles, project)
        }

        private fun allXmlFiles(filesOrDirs: Array<VirtualFile>, project: Project): Sequence<XmlFile> {
            val manager = PsiManager.getInstance(project)
            return allFiles(filesOrDirs)
                .asSequence()
                .mapNotNull { manager.findFile(it) as? XmlFile }
        }

        private fun allFiles(filesOrDirs: Array<VirtualFile>): Collection<VirtualFile> {
            val result = ArrayList<VirtualFile>()
            for (file in filesOrDirs) {
                VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Unit>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        result.add(file)
                        return true
                    }
                })
            }
            return result
        }

    }

    override fun actionPerformed(e: AnActionEvent) {
        val xmlFiles = selectedXmlFiles(e).filter { it.isWritable }.toList()
        convertFiles(xmlFiles)
    }

    override fun update(e: AnActionEvent) {
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val project = e.project ?: return
        e.presentation.isVisible = isAnyXmlFileSelected(project, virtualFiles)
    }
}