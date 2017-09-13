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

package android.arch.paging;

import java.util.List;

public class ListDataSource<T> extends TiledDataSource<T> {
    private List<T> mList;

    ListDataSource(List<T> data) {
        mList = data;
    }

    @Override
    public int loadCount() {
        return mList.size();
    }

    @Override
    public List<T> loadRange(int startPosition, int count) {
        int endExclusive = Math.min(mList.size(), startPosition + count);
        return mList.subList(startPosition, endExclusive);
    }
}
