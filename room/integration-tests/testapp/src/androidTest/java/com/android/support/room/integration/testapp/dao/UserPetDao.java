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

package com.android.support.room.integration.testapp.dao;

import com.android.support.room.Dao;
import com.android.support.room.Query;
import com.android.support.room.integration.testapp.vo.UserAndPet;
import com.android.support.room.integration.testapp.vo.UserAndPetNonNull;

import java.util.List;

@Dao
public interface UserPetDao {
    @Query("SELECT * FROM User u, Pet p WHERE u.mId = p.mUserId")
    List<UserAndPet> loadAll();

    @Query("SELECT * FROM User u LEFT OUTER JOIN Pet p ON u.mId = p.mUserId")
    List<UserAndPet> loadUsers();

    @Query("SELECT * FROM User u LEFT OUTER JOIN Pet p ON u.mId = p.mUserId")
    List<UserAndPetNonNull> loadUsersWithNonNullPet();

    @Query("SELECT * FROM Pet p LEFT OUTER JOIN User u ON u.mId = p.mUserId")
    List<UserAndPet> loadPets();
}
