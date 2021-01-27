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
package androidx.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.get
import java.util.UUID

/**
 * NavControllerViewModel is the always up to date view of the NavController's
 * non configuration state
 */
internal class NavControllerViewModel : ViewModel() {
    private val viewModelStores = mutableMapOf<UUID, ViewModelStore>()

    fun clear(backStackEntryUUID: UUID) {
        // Clear and remove the NavGraph's ViewModelStore
        val viewModelStore = viewModelStores.remove(backStackEntryUUID)
        viewModelStore?.clear()
    }

    override fun onCleared() {
        for (store in viewModelStores.values) {
            store.clear()
        }
        viewModelStores.clear()
    }

    fun getViewModelStore(backStackEntryUUID: UUID): ViewModelStore {
        var viewModelStore = viewModelStores[backStackEntryUUID]
        if (viewModelStore == null) {
            viewModelStore = ViewModelStore()
            viewModelStores[backStackEntryUUID] = viewModelStore
        }
        return viewModelStore
    }

    override fun toString(): String {
        val sb = StringBuilder("NavControllerViewModel{")
        sb.append(Integer.toHexString(System.identityHashCode(this)))
        sb.append("} ViewModelStores (")
        val viewModelStoreIterator: Iterator<UUID> = viewModelStores.keys.iterator()
        while (viewModelStoreIterator.hasNext()) {
            sb.append(viewModelStoreIterator.next())
            if (viewModelStoreIterator.hasNext()) {
                sb.append(", ")
            }
        }
        sb.append(')')
        return sb.toString()
    }

    companion object {
        private val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return NavControllerViewModel() as T
            }
        }

        @JvmStatic
        fun getInstance(viewModelStore: ViewModelStore): NavControllerViewModel {
            val viewModelProvider = ViewModelProvider(viewModelStore, FACTORY)
            return viewModelProvider.get()
        }
    }
}
