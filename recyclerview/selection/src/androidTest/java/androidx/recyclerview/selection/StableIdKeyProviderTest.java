/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.recyclerview.selection;

import static org.mockito.Mockito.when;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StableIdKeyProviderTest {

    private RecyclerView mRecyclerView;
    private StableIdKeyProvider mKeyProvider;

    @Before
    public void setUp() {
        mRecyclerView = Mockito.mock(RecyclerView.class);
        mKeyProvider = new StableIdKeyProvider(mRecyclerView);
    }

    @Test
    public void testOnAttached_NullViewHolder() {
        when(mRecyclerView.findContainingViewHolder(ArgumentMatchers.<View>any())).thenReturn(null);
        mKeyProvider.onAttached(Mockito.mock(View.class));
    }

    @Test
    public void testOnDetatched_NullViewHolder() {
        when(mRecyclerView.findContainingViewHolder(ArgumentMatchers.<View>any())).thenReturn(null);
        mKeyProvider.onDetached(Mockito.mock(View.class));
    }
}
