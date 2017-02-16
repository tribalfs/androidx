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

package com.android.support.apptoolkit.internal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.support.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

@RunWith(JUnit4.class)
@SmallTest
public class SafeIterableMapTest {

    @Test
    public void testToString() {
        SafeIterableMap<Integer, String> map = from(1, 2, 3, 4).to("a", "b", "c", "d");
        assertThat(map.toString(), is("[1=a, 2=b, 3=c, 4=d]"));
    }

    @Test
    public void testEmptyToString() {
        SafeIterableMap<Integer, Boolean> map = mapOf();
        assertThat(map.toString(), is("[]"));
    }

    @Test
    public void testOneElementToString() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1);
        assertThat(map.toString(), is("[1=true]"));
    }


    @Test
    public void testEquality1() {
        SafeIterableMap<Integer, Integer> map1 = from(1, 2, 3, 4).to(10, 20, 30, 40);
        SafeIterableMap<Integer, Integer> map2 = from(1, 2, 3, 4).to(10, 20, 30, 40);
        assertThat(map1.equals(map2), is(true));
    }

    @Test
    public void testEquality2() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        //noinspection ObjectEqualsNull
        assertThat(map.equals(null), is(false));
    }

    @Test
    public void testEquality3() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        //noinspection EqualsBetweenInconvertibleTypes
        assertThat(map.equals(new ArrayList<>()), is(false));
    }

    @Test
    public void testEquality4() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        assertThat(map.equals(new SafeIterableMap<Integer, Boolean>()), is(false));
    }

    @Test
    public void testEquality5() {
        SafeIterableMap<Integer, Boolean> map1 = mapOf(1, 2, 3, 4);
        SafeIterableMap<Integer, Boolean> map2 = mapOf(1);
        assertThat(map1.equals(map2), is(false));
    }

    @Test
    public void testEquality6() {
        SafeIterableMap<Integer, Boolean> map1 = mapOf(1, 2, 3, 4);
        SafeIterableMap<Integer, Boolean> map2 = mapOf(1, 2, 3, 5);
        assertThat(map1.equals(map2), is(false));
    }

    @Test
    public void testEquality7() {
        SafeIterableMap<Integer, Integer> map1 = from(1, 2, 3, 4).to(1, 2, 3, 4);
        SafeIterableMap<Integer, Integer> map2 = from(1, 2, 3, 4).to(1, 2, 3, 5);
        assertThat(map1.equals(map2), is(false));
    }


    @Test
    public void testEquality8() {
        SafeIterableMap<Integer, Boolean> map1 = mapOf();
        SafeIterableMap<Integer, Boolean> map2 = mapOf();
        assertThat(map1.equals(map2), is(true));
    }

    @Test
    public void testEqualityRespectsOrder() {
        SafeIterableMap<Integer, Boolean> map1 = mapOf(1, 2, 3, 4);
        SafeIterableMap<Integer, Boolean> map2 = mapOf(1, 3, 2, 4);
        assertThat(map1.equals(map2), is(false));
    }

    @Test
    public void testPut() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 30, 40);
        assertThat(map.putIfAbsent(5, 10), is((Integer) null));
        assertThat(map, is(from(1, 2, 3, 4, 5).to(10, 20, 30, 40, 10)));
    }

    @Test
    public void testAddExisted() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 261, 40);
        assertThat(map.putIfAbsent(3, 239), is(261));
        assertThat(map, is(from(1, 2, 3, 4).to(10, 20, 261, 40)));
    }

    @Test
    public void testRemoveLast() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 30, 40);
        assertThat(map.remove(4), is(40));
        assertThat(map, is(from(1, 2, 3).to(10, 20, 30)));
    }

    @Test
    public void testRemoveFirst() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        assertThat(map.remove(1), is(true));
        assertThat(map, is(mapOf(2, 3, 4)));
    }

    @Test
    public void testRemoveMiddle() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 30, 40);
        assertThat(map.remove(2), is(20));
        assertThat(map.remove(3), is(30));
        assertThat(map, is(from(1, 4).to(10, 40)));
    }

    @Test
    public void testRemoveNotExisted() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        assertThat(map.remove(5), is((Boolean) null));
        assertThat(map, is(mapOf(1, 2, 3, 4)));
    }

    @Test
    public void testRemoveSole() {
        SafeIterableMap<Integer, Integer> map = from(1).to(261);
        assertThat(map.remove(1), is(261));
        assertThat(map, is(new SafeIterableMap<Integer, Integer>()));
    }

    @Test
    public void testRemoveDuringIteration1() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 30, 40);
        int index = 0;
        int[] expected = new int[]{1, 4};
        for (Map.Entry<Integer, Integer> i : map) {
            assertThat(i.getKey(), is(expected[index++]));
            if (index == 1) {
                assertThat(map.remove(2), is(20));
                assertThat(map.remove(3), is(30));
            }
        }
    }

    @Test
    public void testRemoveDuringIteration2() {
        SafeIterableMap<Integer, Integer> map = from(1, 2).to(10, 20);
        Iterator<Map.Entry<Integer, Integer>> iter = map.iterator();
        assertThat(map.remove(2), is(20));
        assertThat(map.remove(1), is(10));
        assertThat(iter.hasNext(), is(false));
    }

    @Test
    public void testRemoveDuringIteration3() {
        SafeIterableMap<Integer, Integer> map = from(1, 2, 3, 4).to(10, 20, 30, 40);
        int index = 0;
        Iterator<Map.Entry<Integer, Integer>> iter = map.iterator();
        assertThat(map.remove(1), is(10));
        assertThat(map.remove(2), is(20));
        int[] expected = new int[]{3, 4};
        while (iter.hasNext()) {
            assertThat(iter.next().getKey(), is(expected[index++]));
        }
    }

    @Test
    public void testAdditionDuringIteration() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        int[] expected = new int[]{1, 2, 3, 4};
        int index = 0;
        for (Map.Entry<Integer, Boolean> entry : map) {
            assertThat(entry.getKey(), is(expected[index++]));
            if (index == 1) {
                map.putIfAbsent(5, true);
            }
        }
    }

    @Test
    public void testReAdditionDuringIteration() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        int[] expected = new int[]{1, 2, 4};
        int index = 0;
        for (Map.Entry<Integer, Boolean> entry : map) {
            assertThat(entry.getKey(), is(expected[index++]));
            if (index == 1) {
                map.remove(3);
                map.putIfAbsent(3, true);
            }
        }
    }

    @Test
    public void testSize() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        assertThat(map.size(), is(4));
        map.putIfAbsent(5, true);
        map.putIfAbsent(6, true);
        assertThat(map.size(), is(6));
        map.remove(5);
        map.remove(5);
        assertThat(map.size(), is(5));
        map.remove(1);
        map.remove(2);
        map.remove(4);
        map.remove(3);
        map.remove(6);
        assertThat(map.size(), is(0));
        map.putIfAbsent(4, true);
        assertThat(map.size(), is(1));
        assertThat(mapOf().size(), is(0));
    }

    @Test
    public void testIteratorWithAdditions() {
        SafeIterableMap<Integer, Boolean> map = mapOf(1, 2, 3, 4);
        int[] expected = new int[]{1, 2, 3, 5};
        int index = 0;
        Iterator<Map.Entry<Integer, Boolean>> iterator = map.iteratorWithAdditions();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            assertThat(entry.getKey(), is(expected[index++]));
            if (index == 3) {
                map.remove(4);
                map.putIfAbsent(5, true);
            }
        }
    }

    // for most operations we don't care about values, so we create map from key to true
    @SafeVarargs
    private static <K> SafeIterableMap<K, Boolean> mapOf(K... keys) {
        SafeIterableMap<K, Boolean> map = new SafeIterableMap<>();
        for (K key : keys) {
            map.putIfAbsent(key, true);
        }
        return map;
    }

    @SafeVarargs
    private static <K> MapBuilder<K> from(K... keys) {
        return new MapBuilder<>(keys);
    }

    private static class MapBuilder<K> {
        final K[] mKeys;

        MapBuilder(K[] keys) {
            this.mKeys = keys;
        }

        @SafeVarargs
        public final <V> SafeIterableMap<K, V> to(V... values) {
            assertThat("Failed to build Map", mKeys.length, is(values.length));
            SafeIterableMap<K, V> map = new SafeIterableMap<>();
            for (int i = 0; i < mKeys.length; i++) {
                map.putIfAbsent(mKeys[i], values[i]);
            }
            return map;
        }
    }
}


