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

package android.support.tools.jetifier.core.rules

import android.support.tools.jetifier.core.transform.proguard.ProGuardType
import com.google.gson.annotations.SerializedName
import java.util.regex.Pattern

/**
 * Rule that rewrites a Java type or field based on the given arguments.
 *
 * Used in the preprocessor when generating [TypesMap].
 *
 * @param from Regular expression where packages are separated via '/' and inner class separator
 * is "$". Used to match the input type.
 * @param to A string to be used as a replacement if the 'from' pattern is matched. It can also
 * apply groups matched from the original pattern using {x} annotation, e.g. {0}.
 * @param fieldSelectors Collection of regular expressions that are used to match fields. If the
 * type is matched (using 'from') and the field is matched (or the list of fields selectors is
 * empty) the field's type gets rewritten according to the 'to' parameter.
 */
class RewriteRule(
        private val from: String,
        private val to: String,
        private val fieldSelectors: List<String> = emptyList()) {

    // We escape '$' so we don't conflict with regular expression symbols.
    private val inputPattern = Pattern.compile("^${from.replace("$", "\\$")}$")
    private val outputPattern = to.replace("$", "\$")

    private val fields = fieldSelectors.map { Pattern.compile("^$it$") }

    /*
     * Whether this is any type of an ignore rule.
     */
    fun isIgnoreRule() = isRuntimeIgnoreRule() || isPreprocessorOnlyIgnoreRule()

    /*
     * Whether this rules is an ignore rule.
     *
     * Any type matched to [from] will be in such case ignored by the preprocessor (thus missing
     * from the map) but it will be also ignored during rewriting.
     */
    fun isRuntimeIgnoreRule() = to == "ignore"

    /*
     * Whether this rule is an ignore rule that should be used only in the preprocessor.
     *
     * That means that error is still thrown if [from] is found in a library that is being
     * rewritten. Use this for types that are internal to support library. This is weaker version of
     * [isRuntimeIgnoreRule].
     */
    fun isPreprocessorOnlyIgnoreRule() = to == "ignoreInPreprocessorOnly"

    /**
     * Rewrites the given java type. Returns null if this rule is not applicable for the given type.
     */
    fun apply(input: JavaType): TypeRewriteResult {
        if (fields.isNotEmpty()) {
            return TypeRewriteResult.NOT_APPLIED
        }

        return applyInternal(input)
    }

    /**
     * Rewrites the given field type. Returns null if this rule is not applicable for the given
     * type.
     */
    fun apply(inputField: JavaField): FieldRewriteResult {
        val typeRewriteResult = applyInternal(inputField.owner)

        if (typeRewriteResult.isIgnored) {
            return FieldRewriteResult.IGNORED
        }
        if (typeRewriteResult.result == null) {
            return FieldRewriteResult.NOT_APPLIED
        }

        val isFieldInTheFilter = fields.isEmpty()
                || fields.any { it.matcher(inputField.name).matches() }
        if (!isFieldInTheFilter) {
            return FieldRewriteResult.NOT_APPLIED
        }

        return FieldRewriteResult(inputField.renameOwner(typeRewriteResult.result))
    }

    private fun applyInternal(input: JavaType): TypeRewriteResult {
        val matcher = inputPattern.matcher(input.fullName)
        if (!matcher.matches()) {
            return TypeRewriteResult.NOT_APPLIED
        }

        if (isIgnoreRule()) {
            return TypeRewriteResult.IGNORED
        }

        var result = outputPattern
        for (i in 0 until matcher.groupCount()) {
            result = result.replace("{$i}", matcher.group(i + 1))
        }

        return TypeRewriteResult(JavaType(result))
    }

    /*
     * Returns whether this rule is an ignore rule and applies to the given proGuard type.
     */
    fun doesThisIgnoreProGuard(type: ProGuardType): Boolean {
        if (!isIgnoreRule()) {
            return false
        }

        val matcher = inputPattern.matcher(type.value)
        return matcher.matches()
    }

    override fun toString(): String {
        return "$inputPattern -> $outputPattern " + fields.joinToString { it.toString() }
    }

    /** Returns JSON data model of this class */
    fun toJson(): JsonData {
        return JsonData(from, to, fieldSelectors)
    }

    /**
     * JSON data model for [RewriteRule].
     */
    data class JsonData(
            @SerializedName("from")
            val from: String,

            @SerializedName("to")
            val to: String,

            @SerializedName("fieldSelectors")
            val fieldSelectors: List<String>? = null) {

        /** Creates instance of [RewriteRule] */
        fun toRule(): RewriteRule {
            return RewriteRule(from, to, fieldSelectors.orEmpty())
        }
    }

    /**
     * Result of java type rewrite using [RewriteRule]
     */
    data class TypeRewriteResult(val result: JavaType?, val isIgnored: Boolean = false) {

        companion object {
            val NOT_APPLIED = TypeRewriteResult(result = null, isIgnored = false)

            val IGNORED = TypeRewriteResult(result = null, isIgnored = true)
        }
    }

    /**
     * Result of java field rewrite using [RewriteRule]
     */
    data class FieldRewriteResult(val result: JavaField?, val isIgnored: Boolean = false) {

        companion object {
            val NOT_APPLIED = FieldRewriteResult(result = null, isIgnored = false)

            val IGNORED = FieldRewriteResult(result = null, isIgnored = true)
        }
    }
}