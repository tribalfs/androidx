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

package androidx.build

import androidx.build.SupportConfig.DEFAULT_MIN_SDK_VERSION
import groovy.lang.Closure
import org.gradle.api.Project
import java.util.ArrayList

/**
 * Extension for [SupportAndroidLibraryPlugin] and [SupportJavaLibraryPlugin].
 */
open class SupportLibraryExtension(val project: Project) {
    var name: String? = null
    var mavenVersion: Version? = null
    var mavenGroup: String? = null
    var description: String? = null
    var inceptionYear: String? = null
    var url = SUPPORT_URL
    private var licenses: MutableCollection<License> = ArrayList()
    var publish = false
    var failOnUncheckedWarnings = true
    var failOnDeprecationWarnings = true

    var useMetalava = false

    /**
     * It disables docs generation and api tracking for tooling modules like annotation processors.
     * We don't expect such modules to be used by developers as libraries, so we don't guarantee
     * any api stability and don't expose any docs about them.
     */
    var toolingProject = false

    /**
     * If unset minSdkVersion will be [DEFAULT_MIN_SDK_VERSION].
     */
    var minSdkVersion: Int = DEFAULT_MIN_SDK_VERSION

    fun license(closure: Closure<*>): License {
        val license = project.configure(License(), closure) as License
        licenses.add(license)
        return license
    }

    fun getLicenses(): Collection<License> {
        return licenses
    }

    companion object {
        @JvmField
        val ARCHITECTURE_URL =
                "https://developer.android.com/topic/libraries/architecture/index.html"
        @JvmField
        val SUPPORT_URL = "http://developer.android.com/tools/extras/support-library.html"
    }
}

class License {
    var name: String? = null
    var url: String? = null
}
