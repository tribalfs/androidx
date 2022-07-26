/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.baselineprofiles.gradle.buildprovider

import org.gradle.api.Project

/**
 * Allows specifying settings for the Baseline Profiles Plugin.
 */
open class BaselineProfilesApkProviderExtension {

    companion object {

        private const val EXTENSION_NAME = "baselineProfilesApkProvider"

        internal fun registerExtension(project: Project): BaselineProfilesApkProviderExtension {
            val ext = project
                .extensions.findByType(BaselineProfilesApkProviderExtension::class.java)
            if (ext != null) {
                return ext
            }
            return project
                .extensions.create(EXTENSION_NAME, BaselineProfilesApkProviderExtension::class.java)
        }
    }

    /**
     * Keep rule file for the special build for baseline profiles. Note that this file is
     * automatically generated by default, unless a path is specified here. The path is relative
     * to the module directory. The same file is used for all the variants. There should be no
     * need to customize this file.
     */
    var keepRulesFile: String? = null
}
