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

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.os.Parcel
import java.util.UUID

@SuppressLint("BanParcelableUsage")
internal class NavBackStackEntryState : Parcelable {
    val uuid: UUID
    val destinationId: Int
    val args: Bundle?
    val savedState: Bundle

    constructor(entry: NavBackStackEntry) {
        uuid = entry.id
        destinationId = entry.destination.id
        args = entry.arguments
        savedState = Bundle()
        entry.saveState(savedState)
    }

    constructor(inParcel: Parcel) {
        uuid = UUID.fromString(inParcel.readString())
        destinationId = inParcel.readInt()
        args = inParcel.readBundle(javaClass.classLoader)
        savedState = inParcel.readBundle(javaClass.classLoader)!!
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {
        parcel.writeString(uuid.toString())
        parcel.writeInt(destinationId)
        parcel.writeBundle(args)
        parcel.writeBundle(savedState)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<NavBackStackEntryState> =
            object : Parcelable.Creator<NavBackStackEntryState> {
                override fun createFromParcel(inParcel: Parcel): NavBackStackEntryState {
                    return NavBackStackEntryState(inParcel)
                }

                override fun newArray(size: Int): Array<NavBackStackEntryState?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
