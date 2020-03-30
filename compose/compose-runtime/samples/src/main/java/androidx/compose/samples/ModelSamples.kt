/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.compose.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.remember
import androidx.ui.foundation.TextField
import androidx.ui.foundation.Text
import androidx.ui.material.Button
import androidx.ui.foundation.TextFieldValue

@Composable
@Sampled
fun modelSample() {
    @Model
    class LoginState(var username: TextFieldValue, var password: TextFieldValue) {
        fun login() = Api.login(username.text, password.text)
    }

    @Composable
    fun LoginScreen() {
        val model = remember { LoginState(
            TextFieldValue("user"),
            TextFieldValue("pass")
        ) }

        TextField(
            value = model.username,
            onValueChange = { model.username = it }
        )
        TextField(
            value = model.password,
            onValueChange = { model.password = it }
        )
        Button(onClick = { model.login() }) {
            Text("Login")
        }
    }
}
