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
data class DependencyVersionsMap(
        val slVersion: String = "",
        val archVersion: String = "",
        val espressoVersion: String = "",
        val testsVersion: String = "",
        val jankTestHelperVersion: String = "",
        val uiAutomatorVersion: String = ""
    ) {

    companion object {

        val LATEST = DependencyVersionsMap(
            slVersion="1.0.0",
            archVersion = "2.0.0",
            espressoVersion = "3.1.0-alpha1",
            testsVersion = "1.1.0-alpha1",
            jankTestHelperVersion = "1.0.1-alpha1",
            uiAutomatorVersion = "2.2.0-alpha1"
        )

        val ALPHA1 = DependencyVersionsMap(
            slVersion="1.0.0-alpha1",
            archVersion = "2.0.0-alpha1",
            espressoVersion = "3.1.0-alpha1",
            testsVersion = "1.1.0-alpha1",
            jankTestHelperVersion = "1.0.1-alpha1",
            uiAutomatorVersion = "2.2.0-alpha1"
        )

        val LATEST_RELEASED = ALPHA1

        fun parseFromVersionSetTypeId(versionSetType: String?): DependencyVersionsMap {
            versionSetType ?: return DependencyVersionsMap.LATEST

            return when (versionSetType) {
                "alpha1" -> DependencyVersionsMap.ALPHA1
                "latest" -> DependencyVersionsMap.LATEST
                else -> throw IllegalArgumentException(
                    "Version set type contains unsupported version set '$versionSetType'")
            }
        }
    }

    /** Takes a version from a configuration file and rewrites any variables related to the map. */
    fun applyOnVersionRef(version: String): String {
        return when (version) {
            "{slVersion}" -> slVersion
            "{archVersion}" -> archVersion
            "{espressoVersion}" -> espressoVersion
            "{testsVersion}" -> testsVersion
            "{jankTestHelperVersion}" -> jankTestHelperVersion
            "{uiAutomatorVersion}" -> uiAutomatorVersion
            else -> version
        }
    }
}