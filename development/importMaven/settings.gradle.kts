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


pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        val allowJetbrains = "androidx.allowJetbrainsDev"
        if (settings.extra.has(allowJetbrains) && settings.extra.get(allowJetbrains) == "true") {
            maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        val allowJetbrains = "androidx.allowJetbrainsDev"
        if (settings.extra.has(allowJetbrains) && settings.extra.get(allowJetbrains) == "true") {
            maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        }
    }
    versionCatalogs {
        create("importMavenLibs") {
            from(files("importMaven.versions.toml"))
        }
    }
}