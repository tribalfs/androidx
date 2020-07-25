/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.compiler.plugins.kotlin

import androidx.compose.compiler.plugins.kotlin.ComposeErrors.COMPOSABLE_PROPERTY_BACKING_FIELD
import androidx.compose.compiler.plugins.kotlin.ComposeErrors.COMPOSABLE_SUSPEND_FUN
import androidx.compose.compiler.plugins.kotlin.ComposeErrors.COMPOSABLE_VAR
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext

class ComposableDeclarationChecker : DeclarationChecker, StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        if (!platform.isJvm()) return
        container.useInstance(this)
    }

    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (!descriptor.hasComposableAnnotation()) return
        when (declaration) {
            is KtProperty -> checkProperty(declaration, descriptor as PropertyDescriptor, context)
            is KtFunction -> checkFunction(declaration, descriptor as FunctionDescriptor, context)
        }
    }

    private fun checkFunction(
        declaration: KtFunction,
        descriptor: FunctionDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor.isSuspend) {
            context.trace.report(
                COMPOSABLE_SUSPEND_FUN.on(declaration.nameIdentifier ?: declaration)
            )
        }
    }

    private fun checkProperty(
        declaration: KtProperty,
        descriptor: PropertyDescriptor,
        context: DeclarationCheckerContext
    ) {
        val initializer = declaration.initializer
        val name = declaration.nameIdentifier
        if (initializer != null && name != null) {
            context.trace.report(COMPOSABLE_PROPERTY_BACKING_FIELD.on(name))
        }
        if (descriptor.isVar && name != null) {
            context.trace.report(COMPOSABLE_VAR.on(name))
        }
    }
}
