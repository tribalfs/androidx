/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.credentials.webauthn

import androidx.annotation.RestrictTo
import java.security.MessageDigest
import org.json.JSONObject

@RestrictTo(RestrictTo.Scope.LIBRARY)
class AuthenticatorAssertionResponse(
  private val requestOptions: PublicKeyCredentialRequestOptions,
  private val credentialId: ByteArray,
  private val origin: String,
  private val up: Boolean,
  private val uv: Boolean,
  private val be: Boolean,
  private val bs: Boolean,
  private var userHandle: ByteArray,
  private val packageName: String? = null,
  private val clientDataHash: ByteArray? = null,
) : AuthenticatorResponse {
  override var clientJson = JSONObject()
  var authenticatorData: ByteArray
  var signature: ByteArray = byteArrayOf()

  init {
    clientJson.put("type", "webauthn.get")
    clientJson.put("challenge", WebAuthnUtils.b64Encode(requestOptions.challenge))
    clientJson.put("origin", origin)
    if (packageName != null) {
      clientJson.put("androidPackageName", packageName)
    }

    authenticatorData = defaultAuthenticatorData()
  }

  fun defaultAuthenticatorData(): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    val rpHash = md.digest(requestOptions.rpId.toByteArray())
    var flags: Int = 0
    if (up) {
      flags = flags or 0x01
    }
    if (uv) {
      flags = flags or 0x04
    }
    if (be) {
      flags = flags or 0x08
    }
    if (bs) {
      flags = flags or 0x10
    }
    val ret = rpHash + byteArrayOf(flags.toByte()) + byteArrayOf(0, 0, 0, 0)
    return ret
  }

  fun dataToSign(): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    var hash: ByteArray
    if (clientDataHash != null) {
      hash = clientDataHash
    } else {
      hash = md.digest(clientJson.toString().toByteArray())
    }

    return authenticatorData + hash
  }

  override fun json(): JSONObject {
    val clientData = clientJson.toString().toByteArray()
    val response = JSONObject()
    if (clientDataHash == null) {
      response.put("clientDataJSON", WebAuthnUtils.b64Encode(clientData))
    }
    response.put("authenticatorData", WebAuthnUtils.b64Encode(authenticatorData))
    response.put("signature", WebAuthnUtils.b64Encode(signature))
    response.put("userHandle", WebAuthnUtils.b64Encode(userHandle))
    return response
  }
}
