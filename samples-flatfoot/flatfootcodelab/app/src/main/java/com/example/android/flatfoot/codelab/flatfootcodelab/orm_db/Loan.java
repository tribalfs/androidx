/*
 * Copyright 2017, The Android Open Source Project
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

package com.example.android.flatfoot.codelab.flatfootcodelab.orm_db;

import com.android.support.room.ColumnInfo;
import com.android.support.room.Entity;
import com.android.support.room.PrimaryKey;
import com.android.support.room.TypeConverters;

import java.util.Date;


@Entity
@TypeConverters(DateConverter.class)
public class Loan {
    // Fields can be public or private with getters and setters.
    public @PrimaryKey String id;
    public Date startTime;
    public Date endTime;
    @ColumnInfo(name="book_id")
    public String bookId;
    @ColumnInfo(name="user_id")
    public String userId;
}
