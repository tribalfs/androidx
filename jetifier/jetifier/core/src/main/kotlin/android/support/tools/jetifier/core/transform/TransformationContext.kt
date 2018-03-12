/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.support.tools.jetifier.core.transform

import android.support.tools.jetifier.core.config.Config
import android.support.tools.jetifier.core.rules.JavaType
import android.support.tools.jetifier.core.rules.RewriteRule.TypeRewriteResult
import android.support.tools.jetifier.core.transform.proguard.ProGuardType
import java.util.regex.Pattern

/**
 * Context to share the transformation state between individual [Transformer]s.
 */
class TransformationContext(
    val config: Config,
    val rewritingSupportLib: Boolean = false,
    val isInReversedMode: Boolean = false
) {

    // Merges all packages prefixes into one regEx pattern
    private val packagePrefixPattern = Pattern.compile(
        "^(" + config.restrictToPackagePrefixes.map { "($it)" }.joinToString("|") + ").*$")

    /**
     * Whether to use identity if type in our scope is missing instead of throwing an exception.
     */
    val useIdentityIfTypeIsMissing = rewritingSupportLib || isInReversedMode

    /**
     * Whether to skip verification of dependency version match in pom files.
     */
    val ignorePomVersionCheck = rewritingSupportLib || isInReversedMode

    /** Counter for [reportNoMappingFoundFailure] calls. */
    var mappingNotFoundFailuresCount = 0
        private set

    /** Counter for [reportNoProGuardMappingFoundFailure] calls. */
    var proGuardMappingNotFoundFailuresCount = 0
        private set

    /** Counter for [reportNoPackageMappingFoundFailure] calls. */
    var packageMappingNotFoundFailuresCounts = 0

    var libraryName: String = ""

    private var runtimeIgnoreRules =
        (
            if (rewritingSupportLib) {
                config.slRules
            } else {
                config.rewriteRules
            }
        )
        .filter { it.isRuntimeIgnoreRule() }
        .toTypedArray()

    /** Total amount of errors found during the transformation process */
    fun errorsTotal() = mappingNotFoundFailuresCount + proGuardMappingNotFoundFailuresCount +
        packageMappingNotFoundFailuresCounts

    /**
     * Returns whether the given type is eligible for rewrite.
     *
     * If not, the transformers should ignore it.
     */
    fun isEligibleForRewrite(type: JavaType): Boolean {
        if (!isEligibleForRewriteInternal(type.fullName)) {
            return false
        }

        val isIgnored = runtimeIgnoreRules.any { it.apply(type) == TypeRewriteResult.IGNORED }
        return !isIgnored
    }

    /**
     * Returns whether the given ProGuard type reference is eligible for rewrite.
     *
     * Keep in mind that his has limited capabilities - mainly when * is used as a prefix. Rules
     * like *.v7 are not matched by prefix support.v7. So don't rely on it and use
     * the [ProGuardTypesMap] as first.
     */
    fun isEligibleForRewrite(type: ProGuardType): Boolean {
        if (!isEligibleForRewriteInternal(type.value)) {
            return false
        }

        val isIgnored = runtimeIgnoreRules.any { it.doesThisIgnoreProGuard(type) }
        return !isIgnored
    }

    private fun isEligibleForRewriteInternal(type: String): Boolean {
        if (config.restrictToPackagePrefixes.isEmpty()) {
            return false
        }
        return packagePrefixPattern.matcher(type).matches()
    }

    /**
     * Reports that there was a reference found that satisfies [isEligibleForRewrite] but no
     * mapping was found to rewrite it.
     */
    fun reportNoMappingFoundFailure() {
        mappingNotFoundFailuresCount++
    }

    /**
     * Reports that there was a reference found in a ProGuard file that satisfies
     * [isEligibleForRewrite] but no mapping was found to rewrite it.
     */
    fun reportNoProGuardMappingFoundFailure() {
        proGuardMappingNotFoundFailuresCount++
    }

    /**
     * Reports that there was a package reference found in a manifest file during a support library
     * artifact rewrite but no mapping was found for it.
     */
    fun reportNoPackageMappingFoundFailure() {
        packageMappingNotFoundFailuresCounts++
    }
}