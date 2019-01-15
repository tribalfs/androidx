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

package androidx.navigation

import android.net.Uri
import android.os.Bundle
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

@SmallTest
class NavDestinationAndroidTest {
    @Test
    fun matchDeepLink() {
        val destination = NoOpNavigator().createDestination()
        val idArgument = NavArgument.Builder()
            .setType(NavType.IntType)
            .build()
        destination.addArgument("id", idArgument)
        destination.addDeepLink("www.example.com/users/{id}")

        val match = destination.matchDeepLink(
            Uri.parse("https://www.example.com/users/43"))

        assertWithMessage("Deep link should match")
            .that(match)
            .isNotNull()

        assertWithMessage("Deep link should extract id argument correctly")
            .that(match?.second?.getInt("id"))
            .isEqualTo(43)
    }

    @Test
    fun matchDeepLinkBestMatch() {
        val destination = NoOpNavigator().createDestination()

        val idArgument = NavArgument.Builder()
            .setType(NavType.IntType)
            .build()
        destination.addArgument("id", idArgument)
        destination.addDeepLink("www.example.com/users/{id}")

        val postIdArgument = NavArgument.Builder()
            .setType(NavType.IntType)
            .build()
        destination.addArgument("postId", postIdArgument)
        destination.addDeepLink("www.example.com/users/{id}/posts/{postId}")

        val match = destination.matchDeepLink(
            Uri.parse("https://www.example.com/users/43/posts/99"))

        assertWithMessage("Deep link should match")
            .that(match)
            .isNotNull()
        assertWithMessage("Deep link should pick the argument with more matching arguments")
            .that(match?.second?.size())
            .isEqualTo(2)
        assertWithMessage("Deep link should extract id argument correctly")
            .that(match?.second?.getInt("id"))
            .isEqualTo(43)
        assertWithMessage("Deep link should extract postId argument correctly")
            .that(match?.second?.getInt("postId"))
            .isEqualTo(99)
    }

    @Test
    fun addInDefaultArgs() {
        val destination = NoOpNavigator().createDestination()
        val stringArgument = NavArgument.Builder()
            .setType(NavType.StringType)
            .setDefaultValue("aaa")
            .build()
        val intArgument = NavArgument.Builder()
            .setType(NavType.IntType)
            .setDefaultValue(123)
            .build()
        destination.addArgument("stringArg", stringArgument)
        destination.addArgument("intArg", intArgument)

        val bundle = destination.addInDefaultArgs(Bundle().apply {
            putString("stringArg", "bbb")
        })
        assertThat(bundle?.getString("stringArg")).isEqualTo("bbb")
        assertThat(bundle?.getInt("intArg")).isEqualTo(123)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addInDefaultArgsWrong() {
        val destination = NoOpNavigator().createDestination()
        val stringArgument = NavArgument.Builder()
            .setType(NavType.StringType)
            .setDefaultValue("aaa")
            .build()
        val intArgument = NavArgument.Builder()
            .setType(NavType.IntType)
            .setDefaultValue(123)
            .build()
        destination.addArgument("stringArg", stringArgument)
        destination.addArgument("intArg", intArgument)

        destination.addInDefaultArgs(Bundle().apply {
            putInt("stringArg", 123)
        })
    }
}
