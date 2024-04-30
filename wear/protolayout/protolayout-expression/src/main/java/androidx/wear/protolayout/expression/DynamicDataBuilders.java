/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.wear.protolayout.expression;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicBool;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicColor;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicDuration;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedBool;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedColor;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedDuration;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedFloat;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedInstant;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedInt32;
import androidx.wear.protolayout.expression.FixedValueBuilders.FixedString;
import androidx.wear.protolayout.expression.proto.DynamicDataProto;
import androidx.wear.protolayout.protobuf.CodedInputStream;
import androidx.wear.protolayout.protobuf.CodedOutputStream;
import androidx.wear.protolayout.protobuf.ExtensionRegistryLite;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/** Builders for dynamic data value of a provider. */
public final class DynamicDataBuilders {
    private DynamicDataBuilders() {}

    /** Interface defining a dynamic data value. */
    @RequiresSchemaVersion(major = 1, minor = 200)
    public interface DynamicDataValue<T extends DynamicBuilders.DynamicType> {
        /** Get the protocol buffer representation of this object. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        DynamicDataProto.DynamicDataValue toDynamicDataValueProto();

        /**
         * Creates a {@link DynamicDataValue} from a byte array generated by {@link
         * #toDynamicDataValueByteArray()}.
         *
         * @throws IllegalArgumentException if the byte array does not contain a valid serialization
         */
        @NonNull
        static DynamicDataValue<?> fromByteArray(@NonNull byte[] byteArray) {
            return fromByteArray(byteArray, 0, byteArray.length);
        }

        /**
         * Creates a {@link DynamicDataValue} from the provided byte array at the provided offset
         * and length, that was generated by one of the {@link #toDynamicDataValueByteArray}
         * overloads.
         *
         * @throws IllegalArgumentException if the byte array does not contain a valid serialization
         *     in the provided offset and length
         */
        @NonNull
        static DynamicDataValue<?> fromByteArray(
                @NonNull byte[] byteArray, int offset, int length) {
            try {
                return dynamicDataValueFromProto(
                        DynamicDataProto.DynamicDataValue.parseFrom(
                                CodedInputStream.newInstance(byteArray, offset, length),
                                ExtensionRegistryLite.getEmptyRegistry()));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Byte array could not be parsed into DynamicDataValue", e);
            }
        }

        /**
         * Serializes the {@link DynamicDataValue} into a new byte array that can later be used with
         * {@link #fromByteArray(byte[])}.
         */
        @NonNull
        default byte[] toDynamicDataValueByteArray() {
            return toDynamicDataValueProto().toByteArray();
        }

        /**
         * Serializes the {@link DynamicDataValue} into the provided byte array, returning the
         * amount of bytes written, that can later be used with {@code
         * DynamicDataValue.fromByteArray(byteArray, 0, bytesWritten)}.
         *
         * @throws IllegalArgumentException if the byte array is too small
         */
        default int toDynamicDataValueByteArray(@NonNull byte[] byteArray) {
            return toDynamicDataValueByteArray(byteArray, 0, byteArray.length);
        }

        /**
         * Serializes the {@link DynamicDataValue} into the provided byte array, returning the
         * amount of bytes written, limited by the provided offset and length, that can later be
         * used with {@code DynamicDataValue.fromByteArray(byteArray, offset, bytesWritten)}.
         *
         * @throws IllegalArgumentException if the byte array is too small
         */
        default int toDynamicDataValueByteArray(@NonNull byte[] byteArray, int offset, int length) {
            CodedOutputStream stream = CodedOutputStream.newInstance(byteArray, offset, length);
            try {
                toDynamicDataValueProto().writeTo(stream);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Provided byte array not large enough to contain this DynamicDataValue", e);
            }
            return stream.getTotalBytesWritten();
        }

        /** Creates a boolean {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 200)
        static DynamicDataValue<DynamicBool> fromBool(boolean constant) {
            return new FixedBool.Builder().setValue(constant).build();
        }

        /** Creates a int {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 200)
        static DynamicDataValue<DynamicInt32> fromInt(int constant) {
            return new FixedInt32.Builder().setValue(constant).build();
        }

        /** Creates a float {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 200)
        static DynamicDataValue<DynamicFloat> fromFloat(float constant) {
            return new FixedFloat.Builder().setValue(constant).build();
        }

        /** Creates a color {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 200)
        static DynamicDataValue<DynamicColor> fromColor(@ColorInt int constant) {
            return new FixedColor.Builder().setArgb(constant).build();
        }

        /** Creates a string {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 200)
        static DynamicDataValue<DynamicString> fromString(@NonNull String constant) {
            return new FixedString.Builder().setValue(constant).build();
        }

        /** Creates an {@link Instant} {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 300)
        static DynamicDataValue<DynamicInstant> fromInstant(@NonNull Instant constant) {
            return new FixedInstant.Builder().setEpochSeconds(constant.getEpochSecond()).build();
        }

        /** Creates a {@link Duration} {@link DynamicDataValue}. */
        @NonNull
        @RequiresSchemaVersion(major = 1, minor = 300)
        static DynamicDataValue<DynamicDuration> fromDuration(@NonNull Duration constant) {
            return new FixedDuration.Builder().setSeconds(constant.getSeconds()).build();
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains an int value. Otherwise returns
         * false.
         */
        default boolean hasIntValue() {
            return false;
        }

        /**
         * Returns the int value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain an int
         *     value.
         */
        default int getIntValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains a color value. Otherwise returns
         * false.
         */
        default boolean hasColorValue() {
            return false;
        }

        /**
         * Returns the color value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain a color
         *     value.
         */
        default @ColorInt int getColorValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains a boolean value. Otherwise returns
         * false.
         */
        default boolean hasBoolValue() {
            return false;
        }

        /**
         * Returns the boolean value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain a boolean
         *     value.
         */
        default boolean getBoolValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains a float value. Otherwise returns
         * false.
         */
        default boolean hasFloatValue() {
            return false;
        }

        /**
         * Returns the float value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain a float
         *     value.
         */
        default float getFloatValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains a String value. Otherwise returns
         * false.
         */
        default boolean hasStringValue() {
            return false;
        }

        /**
         * Returns the String value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain a String
         *     value.
         */
        default @NonNull String getStringValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains an {@link Instant} value. Otherwise
         * returns false.
         */
        default boolean hasInstantValue() {
            return false;
        }

        /**
         * Returns the {@link Instant} value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain an {@link
         *     Instant} value.
         */
        default @NonNull Instant getInstantValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /**
         * Returns true if the {@link DynamicDataValue} contains an {@link Duration} value.
         * Otherwise returns false.
         */
        default boolean hasDurationValue() {
            return false;
        }

        /**
         * Returns the {@link Duration} value stored in this {@link DynamicDataValue}.
         *
         * @throws IllegalStateException if the {@link DynamicDataValue} doesn't contain an {@link
         *     Duration} value.
         */
        default @NonNull Duration getDurationValue() {
            throw new IllegalStateException("Type mismatch.");
        }

        /** Get the fingerprint for this object or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Nullable
        Fingerprint getFingerprint();

        /** Builder to create {@link DynamicDataValue} objects. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        interface Builder<T extends DynamicBuilders.DynamicType> {

            /** Builds an instance with values accumulated in this Builder. */
            @NonNull
            DynamicDataValue<T> build();
        }
    }

    /**
     * Creates a new wrapper instance from the proto. Intended for testing purposes only. An object
     * created using this method can't be added to any other wrapper.
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static DynamicDataValue<?> dynamicDataValueFromProto(
            @NonNull DynamicDataProto.DynamicDataValue proto) {
        return dynamicDataValueFromProto(proto, null);
    }

    /** Creates a new wrapper instance from the proto. */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static DynamicDataValue<?> dynamicDataValueFromProto(
            @NonNull DynamicDataProto.DynamicDataValue proto, @Nullable Fingerprint fingerprint) {
        if (proto.hasStringVal()) {
            return FixedString.fromProto(proto.getStringVal(), fingerprint);
        }
        if (proto.hasInt32Val()) {
            return FixedInt32.fromProto(proto.getInt32Val(), fingerprint);
        }
        if (proto.hasFloatVal()) {
            return FixedFloat.fromProto(proto.getFloatVal(), fingerprint);
        }
        if (proto.hasBoolVal()) {
            return FixedBool.fromProto(proto.getBoolVal(), fingerprint);
        }
        if (proto.hasColorVal()) {
            return FixedColor.fromProto(proto.getColorVal(), fingerprint);
        }
        if (proto.hasInstantVal()) {
            return FixedInstant.fromProto(proto.getInstantVal(), fingerprint);
        }
        if (proto.hasDurationVal()) {
            return FixedDuration.fromProto(proto.getDurationVal(), fingerprint);
        }
        throw new IllegalStateException("Proto was not a recognised instance of DynamicDataValue");
    }
}
