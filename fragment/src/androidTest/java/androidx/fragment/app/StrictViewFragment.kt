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

package androidx.fragment.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.test.R
import com.google.common.truth.Truth.assertWithMessage

open class StrictViewFragment(
    @LayoutRes contentLayoutId: Int = R.layout.strict_view_fragment
) : StrictFragment(contentLayoutId) {

    internal var onCreateViewCalled: Boolean = false
    internal var onViewCreatedCalled: Boolean = false
    internal var onDestroyViewCalled: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        checkGetActivity()
        checkState("onCreateView", StrictFragment.CREATED)
        return super.onCreateView(inflater, container, savedInstanceState).also {
            onCreateViewCalled = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        checkGetActivity()
        checkState("onViewCreated", StrictFragment.CREATED)
        onViewCreatedCalled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        assertWithMessage("getView returned null in onDestroyView")
            .that(view)
            .isNotNull()
        checkGetActivity()
        checkState("onDestroyView", StrictFragment.CREATED)
        onDestroyViewCalled = true
    }
}
