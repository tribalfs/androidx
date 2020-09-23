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

package com.android.tools.build.jetifier.core.pom

/**
 * Map that provides extra configuration for versions of dependencies generated by Jetifier.
 */
data class DependencyVersions(private val currentSet: Map<String, String>) {

    companion object {

        val EMPTY = DependencyVersions(emptyMap())

        const val DATA_BINDING_VAR_NAME = "newDataBindingVersion"

        const val DEFAULT_DEPENDENCY_SET = "latestReleased"

        fun parseFromVersionSetTypeId(
            versionsMap: DependencyVersionsMap,
            versionSetType: String? = null
        ): DependencyVersions {
            val name = versionSetType ?: DEFAULT_DEPENDENCY_SET

            if (versionsMap.data.isEmpty()) {
                return DependencyVersions(emptyMap())
            }

            val map = versionsMap.data[name]
            if (map == null) {
                throw IllegalArgumentException(
                    "The given versions map is invalid as it does not " +
                        "contain version set called '$name' or maybe you passed incorrect " +
                        "version set identifier?"
                )
            }

            return DependencyVersions(map)
        }
    }

    /**
     * Puts the given version into the map to be referred to using the given variable name.
     *
     * Ignored if null is given.
     *
     * @param newVersion New version to be put into the map
     * @param forVariable Then name of the variable to be used to refer to the version
     */
    fun replaceVersionIfAny(forVariable: String, newVersion: String?): DependencyVersions {
        newVersion ?: return this

        val temp = currentSet.toMutableMap()
        temp[forVariable] = newVersion
        return DependencyVersions(temp)
    }

    /** Takes a version from a configuration file and rewrites any variables related to the map. */
    fun applyOnVersionRef(version: String): String {
        if (version.matches(Regex("^\\{[a-zA-Z0-9]+\\}$"))) {
            val variableName = version.removePrefix("{").removeSuffix("}")
            return currentSet[variableName]
                ?: throw IllegalArgumentException(
                    "The version variable '$variableName' was not found"
                )
        }

        return version
    }

    fun applyOnConfigPomDep(dep: PomDependency): PomDependency {
        return PomDependency(
            groupId = dep.groupId,
            artifactId = dep.artifactId,
            version = applyOnVersionRef(dep.version!!)
        )
    }
}
