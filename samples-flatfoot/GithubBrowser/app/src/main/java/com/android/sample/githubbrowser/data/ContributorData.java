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
package com.android.sample.githubbrowser.data;

import android.os.Parcel;

import com.android.support.room.Entity;

/**
 * Contributor data object.
 */
@Entity
public class ContributorData extends PersonData {
    public int contributions;

    public ContributorData() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeInt(contributions);
    }

    public static final Creator<ContributorData> CREATOR = new Creator<ContributorData>() {
        public ContributorData createFromParcel(Parcel in) {
            return new ContributorData(in);
        }

        public ContributorData[] newArray(int size) {
            return new ContributorData[size];
        }
    };

    private ContributorData(Parcel in) {
        super(in);
        contributions = in.readInt();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ContributorData that = (ContributorData) o;

        return contributions == that.contributions;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + contributions;
        return result;
    }
}
