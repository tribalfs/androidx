/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.car.app.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Tests for the {@link Metadata} class. */
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
public class MetadataTest {
    @Test
    public void setAndGetPlace() {
        Place place = Place.builder(
                LatLng.create(/* latitude= */ 123, /* longitude= */ 456)).build();
        Metadata metadata = Metadata.ofPlace(place);
        assertThat(metadata.getPlace()).isEqualTo(place);

        metadata = Metadata.builder().build();
        assertThat(metadata.getPlace()).isNull();
    }

    @Test
    public void equals() {
        Place place = Place.builder(
                LatLng.create(/* latitude= */ 123, /* longitude= */ 456)).build();
        Metadata metadata = Metadata.builder().setPlace(place).build();

        assertThat(Metadata.builder().setPlace(place).build()).isEqualTo(metadata);
    }

    @Test
    public void notEquals_differentPlace() {
        Place place = Place.builder(
                LatLng.create(/* latitude= */ 123, /* longitude= */ 456)).build();
        Metadata metadata = Metadata.builder().setPlace(place).build();

        Place place2 = Place.builder(
                LatLng.create(/* latitude= */ 456, /* longitude= */ 789)).build();

        assertThat(Metadata.builder().setPlace(place2).build()).isNotEqualTo(metadata);
    }
}
