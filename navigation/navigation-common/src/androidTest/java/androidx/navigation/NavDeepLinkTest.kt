/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.navigation

import android.net.Uri
import androidx.navigation.test.intArgument
import androidx.navigation.test.nullableStringArgument
import androidx.navigation.test.stringArgument
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.fail
import org.junit.Test
import java.io.UnsupportedEncodingException

@SmallTest
class NavDeepLinkTest {

    companion object {
        private const val DEEP_LINK_EXACT_NO_SCHEME = "www.example.com"
        private const val DEEP_LINK_EXACT_HTTP = "http://$DEEP_LINK_EXACT_NO_SCHEME"
        private const val DEEP_LINK_EXACT_HTTPS = "https://$DEEP_LINK_EXACT_NO_SCHEME"
    }

    @Test
    fun deepLinkExactMatch() {
        val deepLink = NavDeepLink(DEEP_LINK_EXACT_HTTP)

        assertWithMessage("HTTP link should match HTTP")
            .that(deepLink.matches(Uri.parse(DEEP_LINK_EXACT_HTTP)))
            .isTrue()
        assertWithMessage("HTTP link should not match HTTPS")
            .that(deepLink.matches(Uri.parse(DEEP_LINK_EXACT_HTTPS)))
            .isFalse()
    }

    @Test
    fun deepLinkExactMatchWithHyphens() {
        val deepLinkString = "android-app://com.example"
        val deepLink = NavDeepLink(deepLinkString)

        assertThat(deepLink.matches(Uri.parse(deepLinkString)))
            .isTrue()
    }

    @Test
    fun deepLinkExactMatchWithPlus() {
        val deepLinkString = "android+app://com.example"
        val deepLink = NavDeepLink(deepLinkString)

        assertThat(deepLink.matches(Uri.parse(deepLinkString)))
            .isTrue()
    }

    @Test
    fun deepLinkExactMatchWithPeriods() {
        val deepLinkString = "android.app://com.example"
        val deepLink = NavDeepLink(deepLinkString)

        assertThat(deepLink.matches(Uri.parse(deepLinkString)))
            .isTrue()
    }

    @Test
    fun deepLinkExactMatchNoScheme() {
        val deepLink = NavDeepLink(DEEP_LINK_EXACT_NO_SCHEME)

        assertWithMessage("No scheme deep links should match http")
            .that(deepLink.matches(Uri.parse(DEEP_LINK_EXACT_HTTP)))
            .isTrue()
        assertWithMessage("No scheme deep links should match https")
            .that(deepLink.matches(Uri.parse(DEEP_LINK_EXACT_HTTPS)))
            .isTrue()
    }

    @Test
    fun deepLinkArgumentMatchWithoutArguments() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = "2"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id)),
            mapOf()
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getString("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkArgumentInvalidMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = "invalid"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id)),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should be null")
            .that(matchArgs)
            .isNull()
    }

    @Test
    fun deepLinkQueryParamArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentInvalidMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = "invalid"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id)),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should be null")
            .that(matchArgs)
            .isNull()
    }

    @Test
    fun deepLinkQueryParamMultipleArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}&myarg={myarg}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val myarg = "test"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument
                .replace("{id}", id.toString()).replace("{myarg}", myarg)),
            mapOf("id" to intArgument(),
                "myarg" to stringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
        assertWithMessage("Args should contain the argument")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo(myarg)
    }

    @Test
    fun deepLinkQueryParamDefaultArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamNullableArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?myarg={myarg}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("myarg" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the argument and it should be null")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo("@null")
    }

    // Ensure case when matching the exact argument query (i.e. param names in braces) is handled
    @Test
    fun deepLinkQueryParamDefaultArgumentMatchParamsInBraces() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    // Ensure case when matching the exact argument query (i.e. param names in braces) is handled
    @Test
    fun deepLinkQueryParamNullableArgumentMatchParamsInBraces() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?myarg={myarg}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument),
            mapOf("myarg" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the argument and it should be null")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo("@null")
    }

    @Test
    fun deepLinkQueryParamMultipleArgumentMatchOptionalDefault() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}&optional={optional}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val optional = "test"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?id={id}".replace("{id}", id.toString())),
            mapOf("id" to intArgument(),
                "optional" to stringArgument(optional))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
        assertWithMessage("Args should contain optional")
            .that(matchArgs?.getString("optional"))
            .isEqualTo(optional)
    }

    @Test
    fun deepLinkQueryParamMultipleArgumentReverseMatchOptionalDefault() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}&optional={optional}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val optional = "test"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?optional={optional}&id={id}"
                .replace("{id}", id.toString())),
            mapOf("id" to intArgument(),
                "optional" to stringArgument(optional))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
        assertWithMessage("Args should contain optional")
            .that(matchArgs?.getString("optional"))
            .isEqualTo(optional)
    }

    @Test
    fun deepLinkQueryParamMultipleArgumentMatchOptionalNullable() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}&optional={optional}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?id={id}".replace("{id}", id.toString())),
            mapOf("id" to intArgument(),
                "optional" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
        assertWithMessage("Args should contain optional")
            .that(matchArgs?.getString("optional"))
            .isEqualTo("@null")
    }

    @Test
    fun deepLinkQueryParamArgumentMatchExtraParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        try {
            deepLink.getMatchingArguments(
                Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?id={id}&invalid={invalid}"
                    .replace("{id}", id.toString())),
                mapOf("id" to intArgument(),
                    "invalid" to nullableStringArgument()))
            fail(
                "Adding parameter that does not exists in the NavDeepLink should throw " +
                        "IllegalArgumentException"
            )
        } catch (e: IllegalArgumentException) {
            assertThat(e)
                .hasMessageThat().contains(
                    "Please ensure the given query parameters are a subset of those in " +
                            "NavDeepLink $deepLink"
                )
        }
    }

    @Test
    fun deepLinkQueryParamArgumentMatchDifferentParamName() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?string={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?string={id}"
                .replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryNullableParamArgumentMatchDifferentParamName() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?string={myarg}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("myarg" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the argument and it should be null")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo("@null")
    }

    @Test
    fun deepLinkQueryDefaultParamArgumentMatchDifferentParamName() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?string={id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentMatchOnlyPartOfParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}L"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryNullableParamArgumentMatchOnlyPartOfParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?myarg={myarg}L"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("myarg" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the argument and it should be null")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo("@null")
    }

    @Test
    fun deepLinkQueryDefaultParamArgumentMatchOnlyPartOfParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?id={id}L"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentMatchMultiArgsOneParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?name={first}_{last}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val first = "Jane"
        val last = "Doe"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{first}", first).replace("{last}", last)),
            mapOf("first" to stringArgument(),
                "last" to stringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the first name")
            .that(matchArgs?.getString("first"))
            .isEqualTo(first)
        assertWithMessage("Args should contain the last name")
            .that(matchArgs?.getString("last"))
            .isEqualTo(last)
    }

    @Test
    fun deepLinkQueryDefaultParamArgumentMatchMultiArgsOneParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?name={first}_{last}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val first = "Jane"
        val last = "Doe"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("first" to stringArgument(first),
                "last" to stringArgument(last))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the first name")
            .that(matchArgs?.getString("first"))
            .isEqualTo(first)
        assertWithMessage("Args should contain the last name")
            .that(matchArgs?.getString("last"))
            .isEqualTo(last)
    }

    @Test
    fun deepLinkQueryParamOneDefaultArgumentMatchMultiArgsOneParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?name={first}_{last}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val first = "Jane"
        val last = "Doe"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?name=Jane_"),
            mapOf("first" to stringArgument(),
                "last" to stringArgument(last))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the first name")
            .that(matchArgs?.getString("first"))
            .isEqualTo(first)
        assertWithMessage("Args should contain the last name")
            .that(matchArgs?.getString("last"))
            .isEqualTo(last)
    }

    @Test
    fun deepLinkQueryNullableParamArgumentMatchMultiArgsOneParam() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?name={first}_{last}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("first" to nullableStringArgument(),
                "last" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the first name")
            .that(matchArgs?.getString("first"))
            .isEqualTo("@null")
        assertWithMessage("Args should contain the last name")
            .that(matchArgs?.getString("last"))
            .isEqualTo("@null")
    }

    @Test
    fun deepLinkQueryParamArgumentWithWildCard() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId=.*-{id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?productId=wildCardMatch-{id}"
                .replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamDefaultArgumentWithWildCard() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId=.*-{id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamNullableArgumentWithWildCard() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId=.*-{myarg}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?productId=wildCardMatch-{myarg}"),
            mapOf("myarg" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain arg as null")
            .that(matchArgs?.getString("myarg"))
            .isEqualTo("@null")
    }

    // Handle the case were the input is wild card and separator with no argument
    @Test
    fun deepLinkQueryParamDefaultArgumentWithWildCardOnly() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId=.*-{id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?productId=.*-"),
            mapOf("id" to intArgument(id))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentWithStarInFront() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId=A*B{id}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?productId=A*B{id}"
                .replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentWithStarInBack() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?productId={id}A*B"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?productId={id}A*B"
                .replace("{id}", id.toString())),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }

    @Test
    fun deepLinkQueryParamArgumentWithRegex() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?path=go/to/{path}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val path = "directions"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?path=go/to/{path}".replace("{path}", path)),
            mapOf("path" to stringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the path")
            .that(matchArgs?.getString("path"))
            .isEqualTo(path)
    }

    @Test
    fun deepLinkQueryParamDefaultArgumentWithRegex() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?path=go/to/{path}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val path = "directions"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("path" to stringArgument(path))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the path")
            .that(matchArgs?.getString("path"))
            .isEqualTo(path)
    }

    @Test
    fun deepLinkQueryParamNullableArgumentWithRegex() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?path=go/to/{path}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users"),
            mapOf("path" to nullableStringArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the path as null")
            .that(matchArgs?.getString("path"))
            .isEqualTo("@null")
    }

    // Handle the case were the input could be entire path except for the argument
    @Test
    fun deepLinkQueryParamDefaultArgumentWithRegexOnly() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users?path=go/to/{path}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val path = "directions"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse("$DEEP_LINK_EXACT_HTTPS/users?path=go/to/"),
            mapOf("path" to stringArgument(path))
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the path")
            .that(matchArgs?.getString("path"))
            .isEqualTo(path)
    }

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun deepLinkArgumentMatchEncoded() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{name}/posts"
        val deepLink = NavDeepLink(deepLinkArgument)

        val name = "John Doe"
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument.replace("{name}", Uri.encode(name))),
            mapOf("name" to stringArgument())
        )

        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the name")
            .that(matchArgs?.getString("name"))
            .isEqualTo(name)
    }

    @Test
    fun deepLinkMultipleArgumentMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts/{postId}"
        val deepLink = NavDeepLink(deepLinkArgument)

        val id = 2
        val postId = 42
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkArgument
                .replace("{id}", id.toString())
                .replace("{postId}", postId.toString())),
            mapOf("id" to intArgument(),
                "postId" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
        assertWithMessage("Args should contain the postId")
            .that(matchArgs?.getInt("postId"))
            .isEqualTo(postId)
    }

    @Test
    fun deepLinkEmptyArgumentNoMatch() {
        val deepLinkArgument = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts"
        val deepLink = NavDeepLink(deepLinkArgument)

        assertThat(deepLink.matches(Uri.parse(deepLinkArgument.replace("{id}", ""))))
            .isFalse()
    }

    @Test
    fun deepLinkPrefixMatch() {
        val deepLinkPrefix = "$DEEP_LINK_EXACT_HTTPS/posts/.*"
        val deepLink = NavDeepLink(deepLinkPrefix)

        assertThat(deepLink.matches(Uri.parse(deepLinkPrefix.replace(".*", "test"))))
            .isTrue()
    }

    @Test
    fun deepLinkWildcardMatch() {
        val deepLinkWildcard = "$DEEP_LINK_EXACT_HTTPS/posts/.*/new"
        val deepLink = NavDeepLink(deepLinkWildcard)

        assertThat(deepLink.matches(Uri.parse(deepLinkWildcard.replace(".*", "test"))))
            .isTrue()
    }

    @Test
    fun deepLinkWildcardBeforeArgumentMatch() {
        val deepLinkMultiple = "$DEEP_LINK_EXACT_HTTPS/users/.*/posts/{postId}"
        val deepLink = NavDeepLink(deepLinkMultiple)

        val postId = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkMultiple
                .replace(".*", "test")
                .replace("{postId}", postId.toString())),
            mapOf("postId" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the postId")
            .that(matchArgs?.getInt("postId"))
            .isEqualTo(postId)
    }

    @Test
    fun deepLinkMultipleMatch() {
        val deepLinkMultiple = "$DEEP_LINK_EXACT_HTTPS/users/{id}/posts/.*"
        val deepLink = NavDeepLink(deepLinkMultiple)

        val id = 2
        val matchArgs = deepLink.getMatchingArguments(
            Uri.parse(deepLinkMultiple
                .replace("{id}", id.toString())
                .replace(".*", "test")),
            mapOf("id" to intArgument())
        )
        assertWithMessage("Args should not be null")
            .that(matchArgs)
            .isNotNull()
        assertWithMessage("Args should contain the id")
            .that(matchArgs?.getInt("id"))
            .isEqualTo(id)
    }
}
