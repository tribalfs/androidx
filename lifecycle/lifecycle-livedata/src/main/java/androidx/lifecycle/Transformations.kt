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
@file:JvmName("Transformations")

package androidx.lifecycle

import androidx.annotation.CheckResult
import androidx.annotation.MainThread

/**
 * Returns a [LiveData] mapped from `this` LiveData by applying [transform] to each value set on
 * `this` LiveData.
 *
 * This method is analogous to [io.reactivex.Observable.map].
 *
 * [transform] will be executed on the main thread.
 *
 * Here is an example mapping a simple `User` struct in a `LiveData` to a
 * `LiveData` containing their full name as a `String`.
 *
 * ```
 * val userLD : LiveData<User> = ...;
 * val userFullNameLD: LiveData<String> = userLD.map { user -> user.firstName + user.lastName }
 * ```
 *
 * @param transform a function to apply to each value set on `source` in order to set
 *                    it on the output `LiveData`
 * @return a LiveData mapped from `source` to type `<Y>` by applying
 * `mapFunction` to each value set.
 */
@JvmName("map")
@MainThread
@CheckResult
fun <X, Y> LiveData<X>.map(
    transform: (@JvmSuppressWildcards X) -> (@JvmSuppressWildcards Y)
): LiveData<Y> {
    val result = MediatorLiveData<Y>()
    result.addSource(this) { x -> result.value = transform(x) }
    return result
}

/**
 * Returns a [LiveData] mapped from the input `this` `LiveData` by applying
 * [transform] to each value set on `this`.
 * <p>
 * The returned `LiveData` delegates to the most recent `LiveData` created by
 * [transform] with the most recent value set to `this`, without
 * changing the reference. In this way [transform] can change the 'backing'
 * `LiveData` transparently to any observer registered to the `LiveData` returned
 * by `switchMap()`.
 *
 * Note that when the backing `LiveData` is switched, no further values from the older
 * `LiveData` will be set to the output `LiveData`. In this way, the method is
 * analogous to [io.reactivex.Observable.switchMap].
 *
 * [transform] will be executed on the main thread.
 *
 * Here is an example class that holds a typed-in name of a user
 * `String` (such as from an `EditText`) in a [MutableLiveData] and
 * returns a `LiveData` containing a List of `User` objects for users that have
 * that name. It populates that `LiveData` by requerying a repository-pattern object
 * each time the typed name changes.
 * <p>
 * This `ViewModel` would permit the observing UI to update "live" as the user ID text
 * changes.
 *
 * ```
 * class UserViewModel: AndroidViewModel {
 *     val nameQueryLiveData : MutableLiveData<String> = ...
 *
 *     fun usersWithNameLiveData(): LiveData<List<String>> = nameQueryLiveData.switchMap {
 *         name -> myDataSource.usersWithNameLiveData(name)
 *     }
 *
 *     fun setNameQuery(val name: String) {
 *         this.nameQueryLiveData.value = name;
 *     }
 * }
 * ```
 *
 * @param transform a function to apply to each value set on `source` to create a
 *                          new delegate `LiveData` for the returned one
 * @return a LiveData mapped from `source` to type `<Y>` by delegating to the LiveData
 * returned by applying `switchMapFunction` to each value set
 */
@JvmName("switchMap")
@MainThread
@CheckResult
fun <X, Y> LiveData<X>.switchMap(
    transform: (@JvmSuppressWildcards X) -> (@JvmSuppressWildcards LiveData<Y>)?
): LiveData<Y> {
    val result = MediatorLiveData<Y>()
    result.addSource(this, object : Observer<X> {
        var liveData: LiveData<Y>? = null

        override fun onChanged(x: X) {
            val newLiveData = transform(x)
            if (liveData === newLiveData) {
                return
            }
            if (liveData != null) {
                result.removeSource(liveData!!)
            }
            liveData = newLiveData
            if (liveData != null) {
                result.addSource(liveData!!) { y -> result.setValue(y) }
            }
        }
    })
    return result
}

/**
 * Creates a new [LiveData] object does not emit a value until the source `this` LiveData value
 * has been changed. The value is considered changed if `equals()` yields `false`.
 *
 * @return a new [LiveData] of type `X`
 */
@JvmName("distinctUntilChanged")
@MainThread
@CheckResult
fun <X> LiveData<X>.distinctUntilChanged(): LiveData<X> {
    val outputLiveData = MediatorLiveData<X>()
    outputLiveData.addSource(this, object : Observer<X> {
        var firstTime = true

        override fun onChanged(currentValue: X) {
            val previousValue = outputLiveData.value
            if (firstTime ||
                previousValue == null && currentValue != null ||
                previousValue != null && previousValue != currentValue
            ) {
                firstTime = false
                outputLiveData.value = currentValue
            }
        }
    })
    return outputLiveData
}