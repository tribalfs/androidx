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

package androidx.media.test.lib;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Constants for calling MediaBrowser2 methods.
 */
public class MediaBrowser2Constants {

    public static final String ROOT_ID = "rootId";
    public static final Bundle EXTRAS = new Bundle();

    public static final String MEDIA_ID_GET_ITEM = "media_id_get_item";

    public static final String PARENT_ID = "parent_id";
    public static final String PARENT_ID_NO_CHILDREN = "parent_id_no_children";
    public static final String PARENT_ID_ERROR = "parent_id_error";

    public static final List<String> GET_CHILDREN_RESULT = new ArrayList<>();
    public static final int CHILDREN_COUNT = 100;

    public static final String SEARCH_QUERY = "search_query";
    public static final String SEARCH_QUERY_TAKES_TIME = "search_query_takes_time";
    public static final int SEARCH_TIME_IN_MS = 5000;
    public static final String SEARCH_QUERY_EMPTY_RESULT = "search_query_empty_result";

    public static final List<String> SEARCH_RESULT = new ArrayList<>();
    public static final int SEARCH_RESULT_COUNT = 50;

    static {
        EXTRAS.putString(ROOT_ID, ROOT_ID);

        GET_CHILDREN_RESULT.clear();
        String getChildrenMediaIdPrefix = "get_children_media_id_";
        for (int i = 0; i < CHILDREN_COUNT; i++) {
            GET_CHILDREN_RESULT.add(getChildrenMediaIdPrefix + i);
        }

        SEARCH_RESULT.clear();
        String getSearchResultMediaIdPrefix = "get_search_result_media_id_";
        for (int i = 0; i < SEARCH_RESULT_COUNT; i++) {
            SEARCH_RESULT.add(getSearchResultMediaIdPrefix + i);
        }
    }

    private MediaBrowser2Constants() {
    }
}
