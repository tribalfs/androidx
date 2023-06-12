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

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * Simple check that the test config templates are able to be parsed as valid xml.
 */
@RunWith(JUnit4::class)
class AndroidTestConfigBuilderTest {

    private lateinit var builder: ConfigBuilder

    @Before
    fun init() {
        builder = ConfigBuilder()
        builder.configName("placeHolderAndroidTest.xml")
            .isBenchmark(false)
            .applicationId("com.androidx.placeholder.Placeholder")
            .isPostsubmit(true)
            .minSdk("15")
            .tag("placeholder_tag")
            .testApkName("placeholder.apk")
            .testApkSha256("123456")
            .testRunner("com.example.Runner")
    }

    @Test
    fun testXmlAgainstGoldenDefault() {
        MatcherAssert.assertThat(
            builder.buildXml(),
            CoreMatchers.`is`(goldenDefaultConfig)
        )
    }

    @Test
    fun testJsonAgainstGoldenDefault() {
        MatcherAssert.assertThat(
            builder.buildJson(),
            CoreMatchers.`is`("""
                {
                  "name": "placeHolderAndroidTest.xml",
                  "minSdkVersion": "15",
                  "testSuiteTags": [
                    "placeholder_tag"
                  ],
                  "testApk": "placeholder.apk",
                  "testApkSha256": "123456",
                  "instrumentationArgs": [
                    {
                      "key": "notAnnotation",
                      "value": "androidx.test.filters.FlakyTest"
                    }
                  ],
                  "additionalApkKeys": []
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testJsonAgainstGoldenAdditionalApkKey() {
        builder.additionalApkKeys(listOf("customKey"))
        MatcherAssert.assertThat(
            builder.buildJson(),
            CoreMatchers.`is`("""
                {
                  "name": "placeHolderAndroidTest.xml",
                  "minSdkVersion": "15",
                  "testSuiteTags": [
                    "placeholder_tag"
                  ],
                  "testApk": "placeholder.apk",
                  "testApkSha256": "123456",
                  "instrumentationArgs": [
                    {
                      "key": "notAnnotation",
                      "value": "androidx.test.filters.FlakyTest"
                    }
                  ],
                  "additionalApkKeys": [
                    "customKey"
                  ]
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testJsonAgainstGoldenPresubmitBenchmark() {
        builder.isBenchmark(true)
            .isPostsubmit(false)
        MatcherAssert.assertThat(
            builder.buildJson(),
            CoreMatchers.`is`("""
                {
                  "name": "placeHolderAndroidTest.xml",
                  "minSdkVersion": "15",
                  "testSuiteTags": [
                    "placeholder_tag"
                  ],
                  "testApk": "placeholder.apk",
                  "testApkSha256": "123456",
                  "instrumentationArgs": [
                    {
                      "key": "notAnnotation",
                      "value": "androidx.test.filters.FlakyTest"
                    },
                    {
                      "key": "androidx.benchmark.dryRunMode.enable",
                      "value": "true"
                    }
                  ],
                  "additionalApkKeys": []
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testJsonAgainstAppTestGolden() {
        builder.appApkName("app-placeholder.apk")
            .appApkSha256("654321")
        MatcherAssert.assertThat(
            builder.buildJson(),
            CoreMatchers.`is`("""
                {
                  "name": "placeHolderAndroidTest.xml",
                  "minSdkVersion": "15",
                  "testSuiteTags": [
                    "placeholder_tag"
                  ],
                  "testApk": "placeholder.apk",
                  "testApkSha256": "123456",
                  "appApk": "app-placeholder.apk",
                  "appApkSha256": "654321",
                  "instrumentationArgs": [
                    {
                      "key": "notAnnotation",
                      "value": "androidx.test.filters.FlakyTest"
                    }
                  ],
                  "additionalApkKeys": []
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testAgainstMediaGoldenDefault() {
        MatcherAssert.assertThat(
            buildMediaJson(
                configName = "foo.json",
                forClient = true,
                clientApkName = "clientPlaceholder.apk",
                clientApkSha256 = "123456",
                isClientPrevious = true,
                isServicePrevious = false,
                minSdk = "15",
                serviceApkName = "servicePlaceholder.apk",
                serviceApkSha256 = "654321",
                tags = listOf(
                    "placeholder_tag",
                    "media_compat"
                ),
            ),
            CoreMatchers.`is`("""
                {
                  "name": "foo.json",
                  "minSdkVersion": "15",
                  "testSuiteTags": [
                    "placeholder_tag",
                    "media_compat"
                  ],
                  "testApk": "clientPlaceholder.apk",
                  "testApkSha256": "123456",
                  "appApk": "servicePlaceholder.apk",
                  "appApkSha256": "654321",
                  "instrumentationArgs": [
                    {
                      "key": "notAnnotation",
                      "value": "androidx.test.filters.FlakyTest"
                    },
                    {
                      "key": "client_version",
                      "value": "previous"
                    },
                    {
                      "key": "service_version",
                      "value": "tot"
                    }
                  ],
                  "additionalApkKeys": []
                }
                """.trimIndent()
            )
        )
    }

    @Test
    fun testValidTestConfigXml_default() {
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_benchmarkTrue() {
        builder.isBenchmark(true)
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_withAppApk() {
        builder.appApkName("Placeholder.apk")
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_presubmitWithAppApk() {
        builder.isPostsubmit(false)
            .appApkName("Placeholder.apk")
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_runAllTests() {
        builder.runAllTests(false)
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_multipleTags() {
        builder.tag("another_tag")
        MatcherAssert.assertThat(builder.tags.size, CoreMatchers.`is`(2))
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_presubmit() {
        builder.isPostsubmit(false)
        validate(builder.buildXml())
    }

    @Test
    fun testValidTestConfigXml_presubmitBenchmark() {
        builder.isPostsubmit(false)
            .isBenchmark(true)
        validate(builder.buildXml())
    }

    private fun validate(xml: String) {
        val parser = SAXParserFactory.newInstance().newSAXParser()
        return parser.parse(
            InputSource(
                StringReader(
                    xml
                )
            ),
            DefaultHandler()
        )
    }
}

private val goldenDefaultConfig = """
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
    <configuration description="Runs tests for the module">
    <object type="module_controller" class="com.android.tradefed.testtype.suite.module.MinApiLevelModuleController">
    <option name="min-api-level" value="15" />
    </object>
    <option name="test-suite-tag" value="placeholder_tag" />
    <option name="config-descriptor:metadata" key="applicationId" value="com.androidx.placeholder.Placeholder" />
    <option name="wifi:disable" value="true" />
    <option name="instrumentation-arg" key="notAnnotation" value="androidx.test.filters.FlakyTest" />
    <include name="google/unbundled/common/setup" />
    <target_preparer class="com.android.tradefed.targetprep.suite.SuiteApkInstaller">
    <option name="cleanup-apks" value="true" />
    <option name="install-arg" value="-t" />
    <option name="test-file-name" value="placeholder.apk" />
    </target_preparer>
    <test class="com.android.tradefed.testtype.AndroidJUnitTest">
    <option name="runner" value="com.example.Runner"/>
    <option name="package" value="com.androidx.placeholder.Placeholder" />
    </test>
    </configuration>
""".trimIndent()
