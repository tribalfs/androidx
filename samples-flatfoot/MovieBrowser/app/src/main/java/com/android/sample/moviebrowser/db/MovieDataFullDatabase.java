/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.sample.moviebrowser.db;

import com.android.sample.moviebrowser.MovieDataFull;
import com.android.support.room.Database;
import com.android.support.room.DatabaseConfiguration;
import com.android.support.room.RoomDatabase;

/**
 * Database for movies.
 */
@Database(entities = MovieDataFull.class)
public abstract class MovieDataFullDatabase extends RoomDatabase {
    /**
     * Creates a RoomDatabase with the given configuration.
     *
     * @param configuration The configuration to setup the database.
     */
    @SuppressWarnings("WeakerAccess")
    public MovieDataFullDatabase(DatabaseConfiguration configuration) {
        super(configuration);
    }

    /**
     * Gets the data access object.
     */
    public abstract MovieDataFullDao getMovieDataFullDao();
}
