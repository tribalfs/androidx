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

package androidx.build

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * [AndroidXMultiplatformExtension] is an extension that wraps specific functionality of the Kotlin
 * multiplatform extension, and applies the Kotlin multiplatform plugin when it is used. The purpose
 * of wrapping is to prevent targets from being added when the platform has not been enabled. e.g.
 * the `macosX64` target is gated on a `project.enableMac` check.
 */
open class AndroidXMultiplatformExtension(val project: Project) {

    // Kotlin multiplatform plugin is only applied if at least one target / sourceset is added.
    private val kotlinExtensionDelegate = lazy {
        project.validateMultiplatformPluginHasNotBeenApplied()
        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
        project.multiplatformExtension!!
    }
    private val kotlinExtension: KotlinMultiplatformExtension by kotlinExtensionDelegate

    /**
     * The list of platforms that have been declared as supported in the build configuration.
     *
     * This may be a superset of the currently enabled platforms in [targetPlatforms].
     */
    val supportedPlatforms: MutableSet<PlatformIdentifier> = mutableSetOf()

    /**
     * The list of platforms that are currently enabled.
     *
     * This will vary across build environments. For example, a project's build configuration may
     * have requested `mac()` but this is not available when building on Linux.
     */
    val targetPlatforms: List<String>
        get() =
            if (kotlinExtensionDelegate.isInitialized()) {
                kotlinExtension.targets.mapNotNull {
                    if (it.targetName != "metadata") {
                        it.targetName
                    } else {
                        null
                    }
                }
            } else {
                throw GradleException("Kotlin multi-platform extension has not been initialized")
            }

    /**
     * Default platform identifier used for specifying POM dependencies.
     *
     * This platform will be added as a dependency to the multi-platform anchor artifact's POM
     * publication. For example, if the anchor artifact is `collection` and the default platform is
     * `jvm`, then the POM for `collection` will express a dependency on `collection-jvm`. This
     * ensures that developers who are silently upgrade to KMP artifacts but are not using Gradle
     * still see working artifacts.
     *
     * If no default was specified and a single platform is requested (ex. using [jvm]), returns the
     * identifier for that platform.
     */
    var defaultPlatform: String? = null
        get() = field ?: supportedPlatforms.singleOrNull()?.id
        set(value) {
            if (value != null) {
                if (supportedPlatforms.none { it.id == value }) {
                    throw GradleException(
                        "Platform $value has not been requested as a target. " +
                            "Available platforms are: " +
                            supportedPlatforms.joinToString(", ") { it.id }
                    )
                }
                if (targetPlatforms.none { it == value }) {
                    throw GradleException(
                        "Platform $value is not available in this build " +
                            "environment. Available platforms are: " +
                            targetPlatforms.joinToString(", ")
                    )
                }
            }
            field = value
        }

    val targets: NamedDomainObjectCollection<KotlinTarget>
        get() = kotlinExtension.targets

    internal fun hasNativeTarget(): Boolean {
        // it is important to check initialized here not to trigger initialization
        return kotlinExtensionDelegate.isInitialized() &&
            targets.any { it.platformType == KotlinPlatformType.native }
    }

    fun sourceSets(closure: Closure<*>) {
        if (kotlinExtensionDelegate.isInitialized()) {
            kotlinExtension.sourceSets.configure(closure)
        }
    }

    /**
     * Sets the default target platform.
     *
     * The default target platform *must* be enabled in all build environments. For projects which
     * request multiple target platforms, this method *must* be called to explicitly specify a
     * default target platform.
     *
     * See [defaultPlatform] for details on how the value is used.
     */
    fun defaultPlatform(value: PlatformIdentifier) {
        defaultPlatform = value.id
    }

    @JvmOverloads
    fun jvm(block: Action<KotlinJvmTarget>? = null): KotlinJvmTarget? {
        supportedPlatforms.add(PlatformIdentifier.JVM)
        return if (project.enableJvm()) {
            kotlinExtension.jvm {
                block?.execute(this)
                // quick fix for b/286852059
                // We need to have either Java plugin or Android plugin for the API
                // files to be generated.
                if (!project.plugins.hasPlugin("com.android.library")) {
                    withJava()
                }
            }
        } else {
            null
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    @JvmOverloads
    fun android(block: Action<KotlinAndroidTarget>? = null): KotlinAndroidTarget? {
        supportedPlatforms.add(PlatformIdentifier.ANDROID)
        return if (project.enableJvm()) {
            kotlinExtension.androidTarget {
                // we need to allow instrumented test to depend on commonTest/jvmTest, which is not
                // default.
                // see https://youtrack.jetbrains.com/issue/KT-62594
                instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
                block?.execute(this)
            }
        } else {
            null
        }
    }

    @JvmOverloads
    fun androidNative(block: Action<KotlinNativeTarget>? = null): List<KotlinNativeTarget> {
        return listOfNotNull(
            androidNativeX86(block),
            androidNativeX64(block),
            androidNativeArm64(block),
            androidNativeArm32(block)
        )
    }

    @JvmOverloads
    fun androidNativeX86(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.ANDROID_NATIVE_X86)
        return if (project.enableNative()) {
            kotlinExtension.androidNativeX86().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun androidNativeX64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.ANDROID_NATIVE_X64)
        return if (project.enableNative()) {
            kotlinExtension.androidNativeX64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun androidNativeArm64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.ANDROID_NATIVE_ARM64)
        return if (project.enableNative()) {
            kotlinExtension.androidNativeArm64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun androidNativeArm32(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.ANDROID_NATIVE_ARM32)
        return if (project.enableNative()) {
            kotlinExtension.androidNativeArm32().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun desktop(block: Action<KotlinJvmTarget>? = null): KotlinJvmTarget? {
        supportedPlatforms.add(PlatformIdentifier.DESKTOP)
        return if (project.enableDesktop()) {
            kotlinExtension.jvm("desktop") { block?.execute(this) }
        } else {
            null
        }
    }

    /** Configures all mac targets supported by AndroidX. */
    @JvmOverloads
    fun mac(block: Action<KotlinNativeTarget>? = null): List<KotlinNativeTarget> {
        return listOfNotNull(macosX64(block), macosArm64(block))
    }

    @JvmOverloads
    fun macosX64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTargetWithHostTests? {
        supportedPlatforms.add(PlatformIdentifier.MAC_OSX_64)
        return if (project.enableMac()) {
            kotlinExtension.macosX64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun macosArm64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTargetWithHostTests? {
        supportedPlatforms.add(PlatformIdentifier.MAC_ARM_64)
        return if (project.enableMac()) {
            kotlinExtension.macosArm64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun iosArm64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.IOS_ARM_64)
        return if (project.enableMac()) {
            kotlinExtension.iosArm64().also { block?.execute(it) }
        } else {
            null
        }
    }

    /** Configures all ios targets supported by AndroidX. */
    @JvmOverloads
    fun ios(block: Action<KotlinNativeTarget>? = null): List<KotlinNativeTarget> {
        return listOfNotNull(iosX64(block), iosArm64(block), iosSimulatorArm64(block))
    }

    @JvmOverloads
    fun iosX64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.IOS_X_64)
        return if (project.enableMac()) {
            kotlinExtension.iosX64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun iosSimulatorArm64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTarget? {
        supportedPlatforms.add(PlatformIdentifier.IOS_SIMULATOR_ARM_64)
        return if (project.enableMac()) {
            kotlinExtension.iosSimulatorArm64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun linux(block: Action<KotlinNativeTarget>? = null): List<KotlinNativeTarget> {
        return listOfNotNull(
            linuxX64(block),
        )
    }

    @JvmOverloads
    fun linuxX64(block: Action<KotlinNativeTarget>? = null): KotlinNativeTargetWithHostTests? {
        supportedPlatforms.add(PlatformIdentifier.LINUX_64)
        return if (project.enableLinux()) {
            kotlinExtension.linuxX64().also { block?.execute(it) }
        } else {
            null
        }
    }

    @JvmOverloads
    fun js(block: Action<KotlinJsTargetDsl>? = null): KotlinJsTargetDsl? {
        supportedPlatforms.add(PlatformIdentifier.JS)
        return if (project.enableJs()) {
            kotlinExtension.js().also { block?.execute(it) }
        } else {
            null
        }
    }

    companion object {
        const val EXTENSION_NAME = "androidXMultiplatform"
    }
}

/**
 * Returns a provider that is set to true if and only if this project has at least 1 kotlin native
 * target (mac, linux, ios).
 */
internal fun Project.hasKotlinNativeTarget(): Provider<Boolean> =
    project.provider {
        project.extensions.getByType(AndroidXMultiplatformExtension::class.java).hasNativeTarget()
    }

fun Project.validatePublishedMultiplatformHasDefault() {
    val extension = project.extensions.getByType(AndroidXMultiplatformExtension::class.java)
    if (extension.defaultPlatform == null && extension.supportedPlatforms.isNotEmpty()) {
        throw GradleException(
            "Project is published and multiple platforms are requested. You " +
                "must explicitly specify androidXMultiplatform.defaultPlatform as one of: " +
                extension.targetPlatforms.joinToString(", ") {
                    "PlatformIdentifier.${PlatformIdentifier.fromId(it)!!.name}"
                }
        )
    }
}
