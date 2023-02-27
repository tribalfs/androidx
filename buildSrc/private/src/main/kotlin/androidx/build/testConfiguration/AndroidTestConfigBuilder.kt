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

package androidx.build.testConfiguration

import com.google.gson.GsonBuilder

class ConfigBuilder {
    lateinit var configName: String
    var appApkName: String? = null
    var appApkSha256: String? = null
    lateinit var applicationId: String
    var isBenchmark: Boolean = false
    var isPostsubmit: Boolean = true
    lateinit var minSdk: String
    var runAllTests: Boolean = true
    var cleanupApks: Boolean = true
    val tags = mutableListOf<String>()
    lateinit var testApkName: String
    lateinit var testApkSha256: String
    lateinit var testRunner: String
    val additionalApkKeys = mutableListOf<String>()

    fun configName(configName: String) = apply { this.configName = configName }
    fun appApkName(appApkName: String) = apply { this.appApkName = appApkName }
    fun appApkSha256(appApkSha256: String) = apply { this.appApkSha256 = appApkSha256 }
    fun applicationId(applicationId: String) = apply { this.applicationId = applicationId }
    fun isBenchmark(isBenchmark: Boolean) = apply { this.isBenchmark = isBenchmark }
    fun isPostsubmit(isPostsubmit: Boolean) = apply { this.isPostsubmit = isPostsubmit }
    fun minSdk(minSdk: String) = apply { this.minSdk = minSdk }
    fun runAllTests(runAllTests: Boolean) = apply { this.runAllTests = runAllTests }
    fun cleanupApks(cleanupApks: Boolean) = apply { this.cleanupApks = cleanupApks }
    fun tag(tag: String) = apply { this.tags.add(tag) }
    fun additionalApkKeys(keys: List<String>) = apply { additionalApkKeys.addAll(keys) }
    fun testApkName(testApkName: String) = apply { this.testApkName = testApkName }
    fun testApkSha256(testApkSha256: String) = apply { this.testApkSha256 = testApkSha256 }
    fun testRunner(testRunner: String) = apply { this.testRunner = testRunner }

    fun buildJson(): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val instrumentationArgs = if (isBenchmark && !isPostsubmit) {
            listOf(
                InstrumentationArg("notAnnotation", "androidx.test.filters.FlakyTest"),
                InstrumentationArg("androidx.benchmark.dryRunMode.enable", "true"),
            )
        } else {
            listOf(
                InstrumentationArg("notAnnotation", "androidx.test.filters.FlakyTest")
            )
        }
        val values = mapOf(
            "name" to configName,
            "minSdkVersion" to minSdk,
            "testSuiteTags" to tags,
            "testApk" to testApkName,
            "testApkSha256" to testApkSha256,
            "appApk" to appApkName,
            "appApkSha256" to appApkSha256,
            "instrumentationArgs" to instrumentationArgs,
            "additionalApkKeys" to additionalApkKeys
        )
        return gson.toJson(values)
    }

    fun buildXml(): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER_AND_LICENSE)
        sb.append(CONFIGURATION_OPEN)
            .append(MIN_API_LEVEL_CONTROLLER_OBJECT.replace("MIN_SDK", minSdk))
        tags.forEach { tag ->
            sb.append(TEST_SUITE_TAG_OPTION.replace("TEST_SUITE_TAG", tag))
        }
        sb.append(MODULE_METADATA_TAG_OPTION.replace("APPLICATION_ID", applicationId))
            .append(WIFI_DISABLE_OPTION)
            .append(FLAKY_TEST_OPTION)
        if (isBenchmark) {
            if (isPostsubmit) {
                sb.append(BENCHMARK_POSTSUBMIT_OPTIONS)
            } else {
                sb.append(BENCHMARK_PRESUBMIT_OPTION)
            }
        }
        sb.append(SETUP_INCLUDE)
            .append(TARGET_PREPARER_OPEN.replace("CLEANUP_APKS", cleanupApks.toString()))
            .append(APK_INSTALL_OPTION.replace("APK_NAME", testApkName))
        if (!appApkName.isNullOrEmpty())
            sb.append(APK_INSTALL_OPTION.replace("APK_NAME", appApkName!!))
        sb.append(TARGET_PREPARER_CLOSE)
            .append(TEST_BLOCK_OPEN)
            .append(RUNNER_OPTION.replace("TEST_RUNNER", testRunner))
            .append(PACKAGE_OPTION.replace("APPLICATION_ID", applicationId))
        if (runAllTests) {
            sb.append(TEST_BLOCK_CLOSE)
        } else {
            sb.append(SMALL_TEST_OPTIONS)
                .append(TEST_BLOCK_CLOSE)
                .append(TEST_BLOCK_OPEN)
            sb.append(RUNNER_OPTION.replace("TEST_RUNNER", testRunner))
                .append(PACKAGE_OPTION.replace("APPLICATION_ID", applicationId))
                .append(MEDIUM_TEST_OPTIONS)
                .append(TEST_BLOCK_CLOSE)
        }
        sb.append(CONFIGURATION_CLOSE)
        return sb.toString()
    }
}

private fun mediaInstrumentationArgsForJson(
    isClientPrevious: Boolean,
    isServicePrevious: Boolean
): List<InstrumentationArg> {
    return listOf(
        if (isClientPrevious) {
            InstrumentationArg(key = "client_version", value = "previous")
        } else {
            InstrumentationArg(key = "client_version", value = "tot")
        },
        if (isServicePrevious) {
            InstrumentationArg(key = "service_version", value = "previous")
        } else {
            InstrumentationArg(key = "service_version", value = "tot")
        }
    )
}

fun buildMediaJson(
    configName: String,
    forClient: Boolean,
    clientApkName: String,
    clientApkSha256: String,
    isClientPrevious: Boolean,
    isServicePrevious: Boolean,
    minSdk: String,
    serviceApkName: String,
    serviceApkSha256: String,
    tags: List<String>,
): String {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val instrumentationArgs =
        listOf(
            InstrumentationArg("notAnnotation", "androidx.test.filters.FlakyTest")
        ) + mediaInstrumentationArgsForJson(
            isClientPrevious = isClientPrevious,
            isServicePrevious = isServicePrevious
        )
    val values = mapOf(
        "name" to configName,
        "minSdkVersion" to minSdk,
        "testSuiteTags" to tags,
        "testApk" to if (forClient) clientApkName else serviceApkName,
        "testApkSha256" to if (forClient) clientApkSha256 else serviceApkSha256,
        "appApk" to if (forClient) serviceApkName else clientApkName,
        "appApkSha256" to if (forClient) serviceApkSha256 else clientApkSha256,
        "instrumentationArgs" to instrumentationArgs,
        "additionalApkKeys" to listOf<String>()
    )
    return gson.toJson(values)
}

private data class InstrumentationArg(
    val key: String,
    val value: String
)

/**
 * These constants are the building blocks of the xml configs, but
 * they aren't very readable as separate chunks. Look to
 * the golden examples at the bottom of
 * {@link androidx.build.testConfiguration.XmlTestConfigVerificationTest}
 * for examples of what the full xml will look like.
 */

private val XML_HEADER_AND_LICENSE = """
    <?xml version="1.0" encoding="utf-8"?>
    <!-- Copyright (C) 2020 The Android Open Source Project
    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions
    and limitations under the License.-->

""".trimIndent()

private val CONFIGURATION_OPEN = """
    <configuration description="Runs tests for the module">

""".trimIndent()

private val CONFIGURATION_CLOSE = """
    </configuration>
""".trimIndent()

private val MIN_API_LEVEL_CONTROLLER_OBJECT = """
    <object type="module_controller" class="com.android.tradefed.testtype.suite.module.MinApiLevelModuleController">
    <option name="min-api-level" value="MIN_SDK" />
    </object>

""".trimIndent()

private val TEST_SUITE_TAG_OPTION = """
    <option name="test-suite-tag" value="TEST_SUITE_TAG" />

""".trimIndent()

private val MODULE_METADATA_TAG_OPTION = """
    <option name="config-descriptor:metadata" key="applicationId" value="APPLICATION_ID" />

""".trimIndent()

private val WIFI_DISABLE_OPTION = """
    <option name="wifi:disable" value="true" />

""".trimIndent()

private val SETUP_INCLUDE = """
    <include name="google/unbundled/common/setup" />

""".trimIndent()

/**
 * Specify the following options on the APK installer:
 * - Pass the -t argument when installing APKs. This allows testonly APKs to be installed, which
 *   includes all APKs built against a pre-release SDK. See b/205571374.
 */
private val TARGET_PREPARER_OPEN = """
    <target_preparer class="com.android.tradefed.targetprep.suite.SuiteApkInstaller">
    <option name="cleanup-apks" value="CLEANUP_APKS" />
    <option name="install-arg" value="-t" />

""".trimIndent()

private val TARGET_PREPARER_CLOSE = """
    </target_preparer>

""".trimIndent()

private val APK_INSTALL_OPTION = """
    <option name="test-file-name" value="APK_NAME" />

""".trimIndent()

private val TEST_BLOCK_OPEN = """
    <test class="com.android.tradefed.testtype.AndroidJUnitTest">

""".trimIndent()

private val TEST_BLOCK_CLOSE = """
    </test>

""".trimIndent()

private val RUNNER_OPTION = """
    <option name="runner" value="TEST_RUNNER"/>

""".trimIndent()

private val PACKAGE_OPTION = """
    <option name="package" value="APPLICATION_ID" />

""".trimIndent()

private val BENCHMARK_PRESUBMIT_OPTION = """
    <option name="instrumentation-arg" key="androidx.benchmark.dryRunMode.enable" value="true" />

""".trimIndent()

private val BENCHMARK_POSTSUBMIT_OPTIONS = """
    <option name="instrumentation-arg" key="androidx.benchmark.output.enable" value="true" />
    <option name="instrumentation-arg" key="listener" value="androidx.benchmark.junit4.InstrumentationResultsRunListener" />

""".trimIndent()

private val FLAKY_TEST_OPTION = """
    <option name="instrumentation-arg" key="notAnnotation" value="androidx.test.filters.FlakyTest" />

""".trimIndent()

private val SMALL_TEST_OPTIONS = """
    <option name="size" value="small" />

""".trimIndent()

private val MEDIUM_TEST_OPTIONS = """
    <option name="size" value="medium" />

""".trimIndent()
