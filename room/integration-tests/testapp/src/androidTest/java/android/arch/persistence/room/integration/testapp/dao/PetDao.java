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

package android.arch.persistence.room.integration.testapp.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.integration.testapp.vo.Pet;
import android.arch.persistence.room.integration.testapp.vo.PetWithToyIds;

import java.util.List;

@Dao
public interface PetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Pet... pets);

    @Insert
    void insertAll(Pet[] pets);

    @Query("SELECT COUNT(*) FROM Pet")
    int count();

    @Query("SELECT * FROM Pet ORDER BY Pet.mPetId ASC")
    List<PetWithToyIds> allPetsWithToyIds();
}
