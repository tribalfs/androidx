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

package a.b;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import androidx.navigation.NavDirections;
import java.lang.String;

public static class Next implements NavDirections {
    @NonNull
    private String main;

    private int mainInt;

    @NonNull
    private String optional = "bla";

    private int optionalInt = 239;

    @Nullable
    private ActivityInfo optionalParcelable = null;

    @NonNull
    private ActivityInfo parcelable;

    public Next(@NonNull String main, int mainInt, @NonNull ActivityInfo parcelable) {
        this.main = main;
        this.mainInt = mainInt;
        this.parcelable = parcelable;
    }

    public Next setMain(@NonNull String main) {
        this.main = main;
        return this;
    }

    public Next setMainInt(int mainInt) {
        this.mainInt = mainInt;
        return this;
    }

    public Next setOptional(@NonNull String optional) {
        this.optional = optional;
        return this;
    }

    public Next setOptionalInt(int optionalInt) {
        this.optionalInt = optionalInt;
        return this;
    }

    public Next setOptionalParcelable(@Nullable ActivityInfo optionalParcelable) {
        this.optionalParcelable = optionalParcelable;
        return this;
    }

    public Next setParcelable(@NonNull ActivityInfo parcelable) {
        this.parcelable = parcelable;
        return this;
    }

    public Bundle getArguments() {
        Bundle __outBundle = new Bundle();
        __outBundle.putString("main", this.main);
        __outBundle.putInt("mainInt", this.mainInt);
        __outBundle.putString("optional", this.optional);
        __outBundle.putInt("optionalInt", this.optionalInt);
        __outBundle.putParcelable("optionalParcelable", this.optionalParcelable);
        __outBundle.putParcelable("parcelable", this.parcelable);
        return __outBundle;
    }

    public int getActionId() {
        return a.b.R.id.next;
    }
}