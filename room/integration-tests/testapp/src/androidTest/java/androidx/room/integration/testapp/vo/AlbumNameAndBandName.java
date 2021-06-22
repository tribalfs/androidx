/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.room.integration.testapp.vo;


public class AlbumNameAndBandName {
    private String mAlbumName;
    private String mBandName;

    public AlbumNameAndBandName(String albumName, String bandName) {
        mAlbumName = albumName;
        mBandName = bandName;
    }

    public String getAlbumName() {
        return mAlbumName;
    }

    public String getBandName() {
        return mBandName;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlbumNameAndBandName that = (AlbumNameAndBandName) o;

        if (mAlbumName != null ? !mAlbumName.equals(that.mAlbumName) :
                that.mAlbumName != null) {
            return false;
        }
        return mBandName != null ? mBandName.equals(that.mBandName) : that.mBandName == null;
    }

    @Override
    public int hashCode() {
        int result = mAlbumName != null ? mAlbumName.hashCode() : 0;
        result = 31 * result + (mBandName != null ? mBandName.hashCode() : 0);
        return result;
    }
}
