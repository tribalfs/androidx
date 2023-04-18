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

// ATTENTION: This file has been automatically generated from Aidl.bnf. Do not edit it manually.

package androidx.com.android.tools.idea.lang.aidl.psi.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;

import androidx.com.android.tools.idea.lang.aidl.psi.AidlPackage;
import androidx.com.android.tools.idea.lang.aidl.psi.AidlQualifiedName;
import androidx.com.android.tools.idea.lang.aidl.psi.AidlVisitor;

import androidx.com.android.tools.idea.lang.aidl.psi.*;

public class AidlPackageImpl extends AidlPsiCompositeElementImpl implements AidlPackage {

  public AidlPackageImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AidlVisitor visitor) {
    visitor.visitPackage(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) accept((AidlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public AidlQualifiedName getQualifiedName() {
    return findNotNullChildByClass(AidlQualifiedName.class);
  }

}
