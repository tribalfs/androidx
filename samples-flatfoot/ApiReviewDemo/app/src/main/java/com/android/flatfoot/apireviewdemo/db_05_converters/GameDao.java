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

package com.android.flatfoot.apireviewdemo.db_05_converters;

import com.android.support.room.Dao;
import com.android.support.room.Query;
import com.android.support.room.TypeConverters;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Dao
@TypeConverters(DateConverter.class)
public abstract class GameDao {
    @Query("select * from Game where `time` BETWEEN :from AND :to")
    abstract public List<Game> findGamesInRange(Date from, Date to);

    public List<Game> listGamesIn1Week() {
        Calendar calendar = Calendar.getInstance();
        Date today = calendar.getTime();
        calendar.set(Calendar.DATE, 7);
        Date nextWeek = calendar.getTime();
        return findGamesInRange(today, nextWeek);
    }
}
