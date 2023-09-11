/*
 * Copyright 2023 The Android Open Source Project
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

@file:Suppress(
    "RedundantVisibilityModifier",
    "NOTHING_TO_INLINE"
)

package androidx.collection

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
// DO NOT MAKE CHANGES to the kotlin source file.
//
// This file was generated from a template in the template directory.
// Make a change to the original template and run the generateCollections.sh script
// to ensure the change is available on all versions of the map.
//
// Note that there are 3 templates for maps, one for object-to-primitive, one
// for primitive-to-object and one for primitive-to-primitive. Also, the
// object-to-object is ScatterMap.kt, which doesn't have a template.
// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

// Default empty map to avoid allocations
private val EmptyIntFloatMap = MutableIntFloatMap(0)

/**
 * Returns an empty, read-only [IntFloatMap].
 */
@Suppress("UNCHECKED_CAST")
public fun emptyIntFloatMap(): IntFloatMap = EmptyIntFloatMap

/**
 * Returns a new [MutableIntFloatMap].
 */
public fun intFloatMapOf(): IntFloatMap = EmptyIntFloatMap

/**
 * Returns a new [IntFloatMap] with the specified contents, given as
 * a list of pairs where the first component is the key and the second
 * is the value. If multiple pairs have the same key, the resulting map
 * will contain the value from the last of those pairs.
 *
 * Note that [pairs] is an allocated array, and each [Pair] is allocated and
 * both the [Int] key and [Float] value are boxed. Use [set] for each
 * entry instead when it is important to reduce allocations.
 */
public fun intFloatMapOf(vararg pairs: Pair<Int, Float>): IntFloatMap =
    MutableIntFloatMap(pairs.size).also { map ->
        pairs.forEach { (key, value) -> map[key] = value }
    }

/**
 * Returns a new [MutableIntFloatMap].
 */
public fun mutableIntFloatMapOf(): MutableIntFloatMap = MutableIntFloatMap()

/**
 * Returns a new [MutableIntFloatMap] with the specified contents, given as
 * a list of pairs where the first component is the key and the second
 * is the value. If multiple pairs have the same key, the resulting map
 * will contain the value from the last of those pairs.
 *
 * Note that [pairs] is an allocated array, and each [Pair] is allocated and
 * both the [Int] key and [Float] value are boxed. Use [set] for each
 * entry instead when it is important to reduce allocations.
 */
public fun mutableIntFloatMapOf(vararg pairs: Pair<Int, Float>): MutableIntFloatMap =
    MutableIntFloatMap(pairs.size).also { map ->
        pairs.forEach { (key, value) -> map[key] = value }
    }

/**
 * [IntFloatMap] is a container with a [Map]-like interface for
 * [Int] primitive keys and [Float] primitive values.
 *
 * The underlying implementation is designed to avoid allocations from boxing,
 * and insertion, removal, retrieval, and iteration operations. Allocations
 * may still happen on insertion when the underlying storage needs to grow to
 * accommodate newly added entries to the table. In addition, this implementation
 * minimizes memory usage by avoiding the use of separate objects to hold
 * key/value pairs.
 *
 * This implementation makes no guarantee as to the order of the keys and
 * values stored, nor does it make guarantees that the order remains constant
 * over time.
 *
 * This implementation is not thread-safe: if multiple threads access this
 * container concurrently, and one or more threads modify the structure of
 * the map (insertion or removal for instance), the calling code must provide
 * the appropriate synchronization. Multiple threads are safe to read from this
 * map concurrently if no write is happening.
 *
 * This implementation is read-only and only allows data to be queried. A
 * mutable implementation is provided by [MutableIntFloatMap].
 *
 * @see [MutableIntFloatMap]
 * @see [ScatterMap]
 */
public sealed class IntFloatMap {
    // NOTE: Our arrays are marked internal to implement inlined forEach{}
    // The backing array for the metadata bytes contains
    // `capacity + 1 + ClonedMetadataCount` entries, including when
    // the table is empty (see [EmptyGroup]).
    @PublishedApi
    @JvmField
    internal var metadata: LongArray = EmptyGroup

    @PublishedApi
    @JvmField
    internal var keys: IntArray = EmptyIntArray

    @PublishedApi
    @JvmField
    internal var values: FloatArray = EmptyFloatArray

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the capacity
    @Suppress("PropertyName")
    @JvmField
    internal var _capacity: Int = 0

    /**
     * Returns the number of key-value pairs that can be stored in this map
     * without requiring internal storage reallocation.
     */
    public val capacity: Int
        get() = _capacity

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the size
    @Suppress("PropertyName")
    @JvmField
    internal var _size: Int = 0

    /**
     * Returns the number of key-value pairs in this map.
     */
    public val size: Int
        get() = _size

    /**
     * Returns `true` if this map has at least one entry.
     */
    public fun any(): Boolean = _size != 0

    /**
     * Returns `true` if this map has no entries.
     */
    public fun none(): Boolean = _size == 0

    /**
     * Indicates whether this map is empty.
     */
    public fun isEmpty(): Boolean = _size == 0

    /**
     * Returns `true` if this map is not empty.
     */
    public fun isNotEmpty(): Boolean = _size != 0

    /**
     * Returns the value corresponding to the given [key].
     * @throws NoSuchElementException if [key] is not in the map
     */
    public operator fun get(key: Int): Float {
        val index = findKeyIndex(key)
        if (index < 0) {
            throw NoSuchElementException("Cannot find value for key $key")
        }
        return values[index]
    }

    /**
     * Returns the value to which the specified [key] is mapped,
     * or [defaultValue] if this map contains no mapping for the key.
     */
    public fun getOrDefault(key: Int, defaultValue: Float): Float {
        val index = findKeyIndex(key)
        if (index >= 0) {
            return values[index]
        }
        return defaultValue
    }

    /**
     * Returns the value for the given [key] if the value is present
     * and not null. Otherwise, returns the result of the [defaultValue]
     * function.
     */
    public inline fun getOrElse(key: Int, defaultValue: () -> Float): Float {
        val index = findKeyIndex(key)
        if (index < 0) {
            return defaultValue()
        }
        return values[index]
    }

    /**
     * Iterates over every key/value pair stored in this map by invoking
     * the specified [block] lambda.
     */
    @PublishedApi
    internal inline fun forEachIndexed(block: (index: Int) -> Unit) {
        val m = metadata
        val lastIndex = m.size - 2 // We always have 0 or at least 2 entries

        for (i in 0..lastIndex) {
            var slot = m[i]
            if (slot.maskEmptyOrDeleted() != BitmaskMsb) {
                // Branch-less if (i == lastIndex) 7 else 8
                // i - lastIndex returns a negative value when i < lastIndex,
                // so 1 is set as the MSB. By inverting and shifting we get
                // 0 when i < lastIndex, 1 otherwise.
                val bitCount = 8 - ((i - lastIndex).inv() ushr 31)
                for (j in 0 until bitCount) {
                    if (isFull(slot and 0xFFL)) {
                        val index = (i shl 3) + j
                        block(index)
                    }
                    slot = slot shr 8
                }
                if (bitCount != 8) return
            }
        }
    }

    /**
     * Iterates over every key/value pair stored in this map by invoking
     * the specified [block] lambda.
     */
    public inline fun forEach(block: (key: Int, value: Float) -> Unit) {
        val k = keys
        val v = values

        forEachIndexed { index ->
            block(k[index], v[index])
        }
    }

    /**
     * Iterates over every key stored in this map by invoking the specified
     * [block] lambda.
     */
    public inline fun forEachKey(block: (key: Int) -> Unit) {
        val k = keys

        forEachIndexed { index ->
            block(k[index])
        }
    }

    /**
     * Iterates over every value stored in this map by invoking the specified
     * [block] lambda.
     */
    public inline fun forEachValue(block: (value: Float) -> Unit) {
        val v = values

        forEachIndexed { index ->
            block(v[index])
        }
    }

    /**
     * Returns true if all entries match the given [predicate].
     */
    public inline fun all(predicate: (Int, Float) -> Boolean): Boolean {
        forEach { key, value ->
            if (!predicate(key, value)) return false
        }
        return true
    }

    /**
     * Returns true if at least one entry matches the given [predicate].
     */
    public inline fun any(predicate: (Int, Float) -> Boolean): Boolean {
        forEach { key, value ->
            if (predicate(key, value)) return true
        }
        return false
    }

    /**
     * Returns the number of entries in this map.
     */
    public fun count(): Int = size

    /**
     * Returns the number of entries matching the given [predicate].
     */
    public inline fun count(predicate: (Int, Float) -> Boolean): Int {
        var count = 0
        forEach { key, value ->
            if (predicate(key, value)) count++
        }
        return count
    }

    /**
     * Returns true if the specified [key] is present in this hash map, false
     * otherwise.
     */
    public operator fun contains(key: Int): Boolean = findKeyIndex(key) >= 0

    /**
     * Returns true if the specified [key] is present in this hash map, false
     * otherwise.
     */
    public fun containsKey(key: Int): Boolean = findKeyIndex(key) >= 0

    /**
     * Returns true if the specified [value] is present in this hash map, false
     * otherwise.
     */
    public fun containsValue(value: Float): Boolean {
        forEachValue { v ->
            if (value == v) return true
        }
        return false
    }

    /**
     * Creates a String from the entries, separated by [separator] and using [prefix] before
     * and [postfix] after, if supplied.
     *
     * When a non-negative value of [limit] is provided, a maximum of [limit] items are used
     * to generate the string. If the collection holds more than [limit] items, the string
     * is terminated with [truncated].
     */
    @JvmOverloads
    public fun joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "", // I know this should be suffix, but this is kotlin's name
        limit: Int = -1,
        truncated: CharSequence = "...",
    ): String = buildString {
        append(prefix)
        var index = 0
        this@IntFloatMap.forEach { key, value ->
            if (index == limit) {
                append(truncated)
                return@buildString
            }
            if (index != 0) {
                append(separator)
            }
            append(key)
            append('=')
            append(value)
            index++
        }
        append(postfix)
    }

    /**
     * Creates a String from the entries, separated by [separator] and using [prefix] before
     * and [postfix] after, if supplied. Each entry is created with [transform].
     *
     * When a non-negative value of [limit] is provided, a maximum of [limit] items are used
     * to generate the string. If the collection holds more than [limit] items, the string
     * is terminated with [truncated].
     */
    @JvmOverloads
    public inline fun joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "", // I know this should be suffix, but this is kotlin's name
        limit: Int = -1,
        truncated: CharSequence = "...",
        crossinline transform: (key: Int, value: Float) -> CharSequence
    ): String = buildString {
        append(prefix)
        var index = 0
        this@IntFloatMap.forEach { key, value ->
            if (index == limit) {
                append(truncated)
                return@buildString
            }
            if (index != 0) {
                append(separator)
            }
            append(transform(key, value))
            index++
        }
        append(postfix)
    }

    /**
     * Returns the hash code value for this map. The hash code the sum of the hash
     * codes of each key/value pair.
     */
    public override fun hashCode(): Int {
        var hash = 0

        forEach { key, value ->
            hash += key.hashCode() xor value.hashCode()
        }

        return hash
    }

    /**
     * Compares the specified object [other] with this hash map for equality.
     * The two objects are considered equal if [other]:
     * - Is a [IntFloatMap]
     * - Has the same [size] as this map
     * - Contains key/value pairs equal to this map's pair
     */
    public override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is IntFloatMap) {
            return false
        }
        if (other.size != size) {
            return false
        }

        forEach { key, value ->
            if (value != other[key]) {
                return false
            }
        }

        return true
    }

    /**
     * Returns a string representation of this map. The map is denoted in the
     * string by the `{}`. Each key/value pair present in the map is represented
     * inside '{}` by a substring of the form `key=value`, and pairs are
     * separated by `, `.
     */
    public override fun toString(): String {
        if (isEmpty()) {
            return "{}"
        }

        val s = StringBuilder().append('{')
        var i = 0
        forEach { key, value ->
            s.append(key)
            s.append("=")
            s.append(value)
            i++
            if (i < _size) {
                s.append(',').append(' ')
            }
        }

        return s.append('}').toString()
    }

    /**
     * Scans the hash table to find the index in the backing arrays of the
     * specified [key]. Returns -1 if the key is not present.
     */
    @PublishedApi
    internal fun findKeyIndex(key: Int): Int {
        val hash = hash(key)
        val hash2 = h2(hash)

        val probeMask = _capacity
        var probeOffset = h1(hash) and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            var m = g.match(hash2)
            while (m.hasNext()) {
                val index = (probeOffset + m.get()) and probeMask
                if (keys[index] == key) {
                    return index
                }
                m = m.next()
            }

            if (g.maskEmpty() != 0L) {
                break
            }

            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }

        return -1
    }
}

/**
 * [MutableIntFloatMap] is a container with a [MutableMap]-like interface for
 * [Int] primitive keys and [Float] primitive values.
 *
 * The underlying implementation is designed to avoid allocations from boxing,
 * and insertion, removal, retrieval, and iteration operations. Allocations
 * may still happen on insertion when the underlying storage needs to grow to
 * accommodate newly added entries to the table. In addition, this implementation
 * minimizes memory usage by avoiding the use of separate objects to hold
 * key/value pairs.
 *
 * This implementation makes no guarantee as to the order of the keys and
 * values stored, nor does it make guarantees that the order remains constant
 * over time.
 *
 * This implementation is not thread-safe: if multiple threads access this
 * container concurrently, and one or more threads modify the structure of
 * the map (insertion or removal for instance), the calling code must provide
 * the appropriate synchronization. Multiple threads are safe to read from this
 * map concurrently if no write is happening.
 *
 * @constructor Creates a new [MutableIntFloatMap]
 * @param initialCapacity The initial desired capacity for this container.
 * the container will honor this value by guaranteeing its internal structures
 * can hold that many entries without requiring any allocations. The initial
 * capacity can be set to 0.
 *
 * @see MutableScatterMap
 */
public class MutableIntFloatMap(
    initialCapacity: Int = DefaultScatterCapacity
) : IntFloatMap() {
    // Number of entries we can add before we need to grow
    private var growthLimit = 0

    init {
        require(initialCapacity >= 0) { "Capacity must be a positive value." }
        initializeStorage(unloadedCapacity(initialCapacity))
    }

    private fun initializeStorage(initialCapacity: Int) {
        val newCapacity = if (initialCapacity > 0) {
            // Since we use longs for storage, our capacity is never < 7, enforce
            // it here. We do have a special case for 0 to create small empty maps
            maxOf(7, normalizeCapacity(initialCapacity))
        } else {
            0
        }
        _capacity = newCapacity
        initializeMetadata(newCapacity)
        keys = IntArray(newCapacity)
        values = FloatArray(newCapacity)
    }

    private fun initializeMetadata(capacity: Int) {
        metadata = if (capacity == 0) {
            EmptyGroup
        } else {
            // Round up to the next multiple of 8 and find how many longs we need
            val size = (((capacity + 1 + ClonedMetadataCount) + 7) and 0x7.inv()) shr 3
            LongArray(size).apply {
                fill(AllEmpty)
            }
        }
        writeRawMetadata(metadata, capacity, Sentinel)
        initializeGrowth()
    }

    private fun initializeGrowth() {
        growthLimit = loadedCapacity(capacity) - _size
    }

    /**
     * Returns the value to which the specified [key] is mapped,
     * if the value is present in the map and not `null`. Otherwise,
     * calls `defaultValue()` and puts the result in the map associated
     * with [key].
     */
    public inline fun getOrPut(key: Int, defaultValue: () -> Float): Float {
        val index = findKeyIndex(key)
        return if (index < 0) {
            val defValue = defaultValue()
            put(key, defValue)
            defValue
        } else {
            values[index]
        }
    }

    /**
     * Creates a new mapping from [key] to [value] in this map. If [key] is
     * already present in the map, the association is modified and the previously
     * associated value is replaced with [value]. If [key] is not present, a new
     * entry is added to the map, which may require to grow the underlying storage
     * and cause allocations.
     */
    public operator fun set(key: Int, value: Float) {
        val index = findAbsoluteInsertIndex(key)
        keys[index] = key
        values[index] = value
    }

    /**
     * Creates a new mapping from [key] to [value] in this map. If [key] is
     * already present in the map, the association is modified and the previously
     * associated value is replaced with [value]. If [key] is not present, a new
     * entry is added to the map, which may require to grow the underlying storage
     * and cause allocations. Return the previous value associated with the [key],
     * or `null` if the key was not present in the map.
     */
    public fun put(key: Int, value: Float) {
        set(key, value)
    }

    /**
     * Puts all the [pairs] into this map, using the first component of the pair
     * as the key, and the second component as the value.
     *
     * Note that [pairs] is an allocated array, and each [Pair] is allocated and
     * both the [Int] key and [Float] value are boxed. Use [set] for each
     * entry instead when it is important to reduce allocations.
     */
    public fun putAll(@Suppress("ArrayReturn") pairs: Array<Pair<Int, Float>>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }

    /**
     * Puts all the key/value mappings in the [from] map into this map.
     */
    public fun putAll(from: IntFloatMap) {
        from.forEach { key, value ->
            this[key] = value
        }
    }

    /**
     * Puts the key/value mapping from the [pair] in this map, using the first
     * element as the key, and the second element as the value.
     *
     * Note that [pair] allocated and both the [Int] key and [Float] value are
     * boxed. Use [set] instead when it is important to reduce allocations.
     */
    public inline operator fun plusAssign(pair: Pair<Int, Float>) {
        this[pair.first] = pair.second
    }

    /**
     * Puts all the [pairs] into this map, using the first component of the pair
     * as the key, and the second component as the value.
     *
     * Note that [pairs] is an allocated array, and each [Pair] is allocated and
     * both the [Int] key and [Float] value are boxed. Use [set] for each
     * entry instead when it is important to reduce allocations.
     */
    public inline operator fun plusAssign(
        @Suppress("ArrayReturn") pairs: Array<Pair<Int, Float>>
    ): Unit = putAll(pairs)

    /**
     * Puts all the key/value mappings in the [from] map into this map.
     */
    public inline operator fun plusAssign(from: IntFloatMap): Unit = putAll(from)

    /**
     * Removes the specified [key] and its associated value from the map.
     */
    public fun remove(key: Int) {
        val index = findKeyIndex(key)
        if (index >= 0) {
            removeValueAt(index)
        }
    }

    /**
     * Removes the specified [key] and its associated value from the map if the
     * associated value equals [value]. Returns whether the removal happened.
     */
    public fun remove(key: Int, value: Float): Boolean {
        val index = findKeyIndex(key)
        if (index >= 0) {
            if (values[index] == value) {
                removeValueAt(index)
                return true
            }
        }
        return false
    }

    /**
     * Removes any mapping for which the specified [predicate] returns true.
     */
    public fun removeIf(predicate: (Int, Float) -> Boolean) {
        forEachIndexed { index ->
            if (predicate(keys[index], values[index])) {
                removeValueAt(index)
            }
        }
    }

    /**
     * Removes the specified [key] and its associated value from the map.
     */
    public inline operator fun minusAssign(key: Int) {
        remove(key)
    }

    /**
     * Removes the specified [keys] and their associated value from the map.
     */
    public inline operator fun minusAssign(@Suppress("ArrayReturn") keys: IntArray) {
        for (key in keys) {
            remove(key)
        }
    }

    /**
     * Removes the specified [keys] and their associated value from the map.
     */
    public inline operator fun minusAssign(keys: IntSet) {
        keys.forEach { key ->
            remove(key)
        }
    }

    /**
     * Removes the specified [keys] and their associated value from the map.
     */
    public inline operator fun minusAssign(keys: IntList) {
        keys.forEach { key ->
            remove(key)
        }
    }

    private fun removeValueAt(index: Int) {
        _size -= 1

        // TODO: We could just mark the entry as empty if there's a group
        //       window around this entry that was already empty
        writeMetadata(index, Deleted)
    }

    /**
     * Removes all mappings from this map.
     */
    public fun clear() {
        _size = 0
        if (metadata !== EmptyGroup) {
            metadata.fill(AllEmpty)
            writeRawMetadata(metadata, _capacity, Sentinel)
        }
        initializeGrowth()
    }

    /**
     * Scans the hash table to find the index at which we can store a value
     * for the give [key]. If the key already exists in the table, its index
     * will be returned, otherwise the index of an empty slot will be returned.
     * Calling this function may cause the internal storage to be reallocated
     * if the table is full.
     */
    private fun findAbsoluteInsertIndex(key: Int): Int {
        val hash = hash(key)
        val hash1 = h1(hash)
        val hash2 = h2(hash)

        val probeMask = _capacity
        var probeOffset = hash1 and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            var m = g.match(hash2)
            while (m.hasNext()) {
                val index = (probeOffset + m.get()) and probeMask
                if (keys[index] == key) {
                    return index
                }
                m = m.next()
            }

            if (g.maskEmpty() != 0L) {
                break
            }

            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }

        var index = findFirstAvailableSlot(hash1)
        if (growthLimit == 0 && !isDeleted(metadata, index)) {
            adjustStorage()
            index = findFirstAvailableSlot(hash1)
        }

        _size += 1
        growthLimit -= if (isEmpty(metadata, index)) 1 else 0
        writeMetadata(index, hash2.toLong())

        return index
    }

    /**
     * Finds the first empty or deleted slot in the table in which we can
     * store a value without resizing the internal storage.
     */
    private fun findFirstAvailableSlot(hash1: Int): Int {
        val probeMask = _capacity
        var probeOffset = hash1 and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            val m = g.maskEmptyOrDeleted()
            if (m != 0L) {
                return (probeOffset + m.lowestBitSet()) and probeMask
            }
            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }
    }

    /**
     * Trims this [MutableIntFloatMap]'s storage so it is sized appropriately
     * to hold the current mappings.
     *
     * Returns the number of empty entries removed from this map's storage.
     * Returns be 0 if no trimming is necessary or possible.
     */
    public fun trim(): Int {
        val previousCapacity = _capacity
        val newCapacity = normalizeCapacity(unloadedCapacity(_size))
        if (newCapacity < previousCapacity) {
            resizeStorage(newCapacity)
            return previousCapacity - _capacity
        }
        return 0
    }

    /**
     * Grow internal storage if necessary. This function can instead opt to
     * remove deleted entries from the table to avoid an expensive reallocation
     * of the underlying storage. This "rehash in place" occurs when the
     * current size is <= 25/32 of the table capacity. The choice of 25/32 is
     * detailed in the implementation of abseil's `raw_hash_set`.
     */
    private fun adjustStorage() {
        if (_capacity > GroupWidth && _size.toULong() * 32UL <= _capacity.toULong() * 25UL) {
            // TODO: Avoid resize and drop deletes instead
            resizeStorage(nextCapacity(_capacity))
        } else {
            resizeStorage(nextCapacity(_capacity))
        }
    }

    private fun resizeStorage(newCapacity: Int) {
        val previousMetadata = metadata
        val previousKeys = keys
        val previousValues = values
        val previousCapacity = _capacity

        initializeStorage(newCapacity)

        val newKeys = keys
        val newValues = values

        for (i in 0 until previousCapacity) {
            if (isFull(previousMetadata, i)) {
                val previousKey = previousKeys[i]
                val hash = hash(previousKey)
                val index = findFirstAvailableSlot(h1(hash))

                writeMetadata(index, h2(hash).toLong())
                newKeys[index] = previousKey
                newValues[index] = previousValues[i]
            }
        }
    }

    /**
     * Writes the "H2" part of an entry into the metadata array at the specified
     * [index]. The index must be a valid index. This function ensures the
     * metadata is also written in the clone area at the end.
     */
    private inline fun writeMetadata(index: Int, value: Long) {
        val m = metadata
        writeRawMetadata(m, index, value)

        // Mirroring
        val c = _capacity
        val cloneIndex = ((index - ClonedMetadataCount) and c) +
            (ClonedMetadataCount and c)
        writeRawMetadata(m, cloneIndex, value)
    }
}
