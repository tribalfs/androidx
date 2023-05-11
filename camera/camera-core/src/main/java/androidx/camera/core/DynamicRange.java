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

package androidx.camera.core;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A representation of the dynamic range of an image. */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class DynamicRange {
    /**
     * An unspecified dynamic range encoding which allows the device to determine the underlying
     * dynamic range encoding.
     */
    public static final int ENCODING_UNSPECIFIED = 0;

    /** Standard Dynamic Range (SDR) encoding. */
    public static final int ENCODING_SDR = 1;

    //------------------------------------------------------------------------------//
    //                            HDR Encodings                                     //
    //------------------------------------------------------------------------------//
    /**
     * An unspecified dynamic range encoding which allows the device to determine the
     * underlying dynamic range encoding, limited to High Dynamic Range (HDR) encodings.
     */
    public static final int ENCODING_HDR_UNSPECIFIED = 2;
    /** Hybrid Log Gamma (HLG) dynamic range encoding. */
    public static final int ENCODING_HLG = ENCODING_HDR_UNSPECIFIED + 1;
    /** HDR10 dynamic range encoding. */
    public static final int ENCODING_HDR10 = ENCODING_HDR_UNSPECIFIED + 2;
    /** HDR10+ dynamic range encoding. */
    public static final int ENCODING_HDR10_PLUS = ENCODING_HDR_UNSPECIFIED + 3;
    /** Dolby Vision dynamic range encoding. */
    public static final int ENCODING_DOLBY_VISION = ENCODING_HDR_UNSPECIFIED + 4;
    //------------------------------------------------------------------------------//

    /** Bit depth is unspecified and may be determined automatically by the device. */
    public static final int BIT_DEPTH_UNSPECIFIED = 0;
    /** Eight-bit bit depth. */
    public static final int BIT_DEPTH_8_BIT = 8;
    /** Ten-bit bit depth. */
    public static final int BIT_DEPTH_10_BIT = 10;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({ENCODING_UNSPECIFIED, ENCODING_SDR, ENCODING_HDR_UNSPECIFIED, ENCODING_HLG,
            ENCODING_HDR10,
            ENCODING_HDR10_PLUS, ENCODING_DOLBY_VISION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DynamicRangeEncoding {
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({BIT_DEPTH_UNSPECIFIED, BIT_DEPTH_8_BIT, BIT_DEPTH_10_BIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BitDepth {
    }

    /**
     * A dynamic range with unspecified encoding and bit depth
     *
     * <p>The dynamic range is unspecified and may defer to device defaults when used to select a
     * dynamic range.
     */
    @NonNull
    public static final DynamicRange UNSPECIFIED = new DynamicRange(ENCODING_UNSPECIFIED,
            BIT_DEPTH_UNSPECIFIED);

    /** A dynamic range representing 8-bit standard dynamic range (SDR). */
    @NonNull
    public static final DynamicRange SDR = new DynamicRange(ENCODING_SDR, BIT_DEPTH_8_BIT);

    /**
     * A dynamic range representing 10-bit high dynamic range (HDR) with unspecified encoding.
     *
     * <p>The HDR encoding is unspecified, and may defer to device defaults
     * when used to select a dynamic range. In this case, the dynamic range will be limited to
     * 10-bit high dynamic ranges.
     */
    @NonNull
    public static final DynamicRange HDR_UNSPECIFIED_10_BIT =
            new DynamicRange(ENCODING_HDR_UNSPECIFIED, BIT_DEPTH_10_BIT);

    private final @DynamicRangeEncoding int mEncoding;
    private final @BitDepth int mBitDepth;

    /**
     * Creates a dynamic range representation from a encoding and bit depth.
     *
     * <p>This constructor is left public for testing purposes. It does not do any verification that
     * the provided arguments are a valid combination of encoding and bit depth.
     *
     * @param encoding   The dynamic range encoding.
     * @param bitDepth The bit depth.
     */
    public DynamicRange(
            @DynamicRangeEncoding int encoding,
            @BitDepth int bitDepth) {
        mEncoding = encoding;
        mBitDepth = bitDepth;
    }

    /**
     * Returns the dynamic range encoding.
     *
     * @return The dynamic range encoding. Possible values are {@link #ENCODING_SDR},
     * {@link #ENCODING_HLG}, {@link #ENCODING_HDR10}, {@link #ENCODING_HDR10_PLUS}, or
     * {@link #ENCODING_DOLBY_VISION}.
     */
    @DynamicRangeEncoding
    public int getEncoding() {
        return mEncoding;
    }

    /**
     * Returns the bit depth used by this dynamic range configuration.
     *
     * <p>Common values are {@link #BIT_DEPTH_8_BIT}, such as for {@link #ENCODING_SDR} or
     * {@link #BIT_DEPTH_10_BIT}, such as for {@link #ENCODING_HDR10}.
     *
     * @return The bit depth. Possible values are {@link #BIT_DEPTH_8_BIT},
     * {@link #BIT_DEPTH_10_BIT}, or {@link #BIT_DEPTH_UNSPECIFIED}.
     */
    @BitDepth
    public int getBitDepth() {
        return mBitDepth;
    }

    @NonNull
    @Override
    public String toString() {
        return "DynamicRange@" + Integer.toHexString(System.identityHashCode(this)) + "{"
                + "encoding=" + getEncodingLabel(mEncoding) + ", "
                + "bitDepth=" + mBitDepth
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof DynamicRange) {
            DynamicRange that = (DynamicRange) o;
            return this.mEncoding == that.getEncoding()
                    && this.mBitDepth == that.getBitDepth();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode *= 1000003;
        hashCode ^= mEncoding;
        hashCode *= 1000003;
        hashCode ^= mBitDepth;
        return hashCode;
    }

    @NonNull
    private static String getEncodingLabel(@DynamicRangeEncoding int encoding) {
        switch (encoding) {
            case ENCODING_UNSPECIFIED: return "UNSPECIFIED";
            case ENCODING_SDR: return "SDR";
            case ENCODING_HDR_UNSPECIFIED: return "HDR_UNSPECIFIED";
            case ENCODING_HLG: return "HLG";
            case ENCODING_HDR10: return "HDR10";
            case ENCODING_HDR10_PLUS: return "HDR10_PLUS";
            case ENCODING_DOLBY_VISION: return "DOLBY_VISION";
        }

        return "<Unknown>";
    }
}
