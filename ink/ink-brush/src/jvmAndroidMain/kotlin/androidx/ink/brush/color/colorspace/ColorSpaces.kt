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

@file:Suppress("NOTHING_TO_INLINE")

package androidx.ink.brush.color.colorspace

import androidx.annotation.RestrictTo
import androidx.annotation.Size
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object ColorSpaces {
    internal val SrgbPrimaries = floatArrayOf(0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f)
    internal val Ntsc1953Primaries = floatArrayOf(0.67f, 0.33f, 0.21f, 0.71f, 0.14f, 0.08f)
    internal val Bt2020Primaries = floatArrayOf(0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f)
    internal val SrgbTransferParameters =
        TransferParameters(2.4, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045)
    private val NoneTransferParameters =
        TransferParameters(2.2, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045)

    // HLG transfer with an SDR whitepoint of 203 nits
    internal val Bt2020HlgTransferParameters =
        TransferParameters(
            gamma = TypeHLGish,
            a = 2.0,
            b = 2.0,
            c = 1 / 0.17883277,
            d = 0.28466892,
            e = 0.55991073,
            f = -0.685490157
        )

    // PQ transfer with an SDR whitepoint of 203 nits
    internal val Bt2020PqTransferParameters =
        TransferParameters(
            gamma = TypePQish,
            a = -1.555223,
            b = 1.860454,
            c = 32 / 2523.0,
            d = 2413 / 128.0,
            e = -2392 / 128.0,
            f = 8192 / 1305.0
        )

    /**
     * [RGB][Rgb] color space sRGB standardized as IEC 61966-2.1:1999.
     * [See details on sRGB color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#SRGB)
     */
    public val Srgb: Rgb =
        Rgb("sRGB IEC61966-2.1", SrgbPrimaries, Illuminant.D65, SrgbTransferParameters, id = 0)

    /**
     * [RGB][Rgb] color space sRGB standardized as IEC 61966-2.1:1999.
     * [See details on Linear sRGB color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#LINEAR_SRGB)
     */
    public val LinearSrgb: Rgb =
        Rgb("sRGB IEC61966-2.1 (Linear)", SrgbPrimaries, Illuminant.D65, 1.0, 0.0f, 1.0f, id = 1)

    /**
     * [RGB][Rgb] color space scRGB-nl standardized as IEC 61966-2-2:2003.
     * [See details on Extended sRGB color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#EXTENDED_SRGB)
     */
    public val ExtendedSrgb: Rgb =
        Rgb(
            "scRGB-nl IEC 61966-2-2:2003",
            SrgbPrimaries,
            Illuminant.D65,
            null,
            { x -> absRcpResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4) },
            { x -> absResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4) },
            -0.799f,
            2.399f,
            SrgbTransferParameters,
            id = 2
        )

    /**
     * [RGB][Rgb] color space scRGB standardized as IEC 61966-2-2:2003.
     * [See details on Linear Extended sRGB color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#LINEAR_EXTENDED_SRGB)
     */
    public val LinearExtendedSrgb: Rgb =
        Rgb("scRGB IEC 61966-2-2:2003", SrgbPrimaries, Illuminant.D65, 1.0, -0.5f, 7.499f, id = 3)

    /**
     * [RGB][Rgb] color space BT.709 standardized as Rec. ITU-R BT.709-5.
     * [See details on BT.709 color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#BT_709)
     */
    public val Bt709: Rgb =
        Rgb(
            "Rec. ITU-R BT.709-5",
            floatArrayOf(0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f),
            Illuminant.D65,
            TransferParameters(1 / 0.45, 1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081),
            id = 4
        )

    /**
     * [RGB][Rgb] color space BT.2020 standardized as Rec. ITU-R BT.2020-1.
     * [See details on BT.2020 color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#BT_2020)
     */
    public val Bt2020: Rgb =
        Rgb(
            "Rec. ITU-R BT.2020-1",
            floatArrayOf(0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f),
            Illuminant.D65,
            TransferParameters(1 / 0.45, 1 / 1.0993, 0.0993 / 1.0993, 1 / 4.5, 0.08145),
            id = 5
        )

    /**
     * [RGB][Rgb] color space DCI-P3 standardized as SMPTE RP 431-2-2007.
     * [See details on DCI-P3 color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#DCI_P3)
     */
    public val DciP3: Rgb =
        Rgb(
            "SMPTE RP 431-2-2007 DCI (P3)",
            floatArrayOf(0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f),
            WhitePoint(0.314f, 0.351f),
            2.6,
            0.0f,
            1.0f,
            id = 6
        )

    /**
     * [RGB][Rgb] color space Display P3 based on SMPTE RP 431-2-2007 and IEC 61966-2.1:1999.
     * [See details on Display P3 color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#DISPLAY_P3)
     */
    public val DisplayP3: Rgb =
        Rgb(
            "Display P3",
            floatArrayOf(0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f),
            Illuminant.D65,
            SrgbTransferParameters,
            id = 7
        )

    /**
     * [RGB][Rgb] color space NTSC, 1953 standard.
     * [See details on NTSC 1953 color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#NTSC_1953)
     */
    public val Ntsc1953: Rgb =
        Rgb(
            "NTSC (1953)",
            Ntsc1953Primaries,
            Illuminant.C,
            TransferParameters(1 / 0.45, 1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081),
            id = 8
        )

    /**
     * [RGB][Rgb] color space SMPTE C.
     * [See details on SMPTE C color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#SMPTE_C)
     */
    public val SmpteC: Rgb =
        Rgb(
            "SMPTE-C RGB",
            floatArrayOf(0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f),
            Illuminant.D65,
            TransferParameters(1 / 0.45, 1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081),
            id = 9
        )

    /**
     * [RGB][Rgb] color space Adobe RGB (1998).
     * [See details on Adobe RGB (1998) color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#ADOBE_RGB)
     */
    public val AdobeRgb: Rgb =
        Rgb(
            "Adobe RGB (1998)",
            floatArrayOf(0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f),
            Illuminant.D65,
            2.2,
            0.0f,
            1.0f,
            id = 10
        )

    /**
     * [RGB][Rgb] color space ProPhoto RGB standardized as ROMM RGB ISO 22028-2:2013.
     * [See details on ProPhoto RGB color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#PRO_PHOTO_RGB)
     */
    public val ProPhotoRgb: Rgb =
        Rgb(
            "ROMM RGB ISO 22028-2:2013",
            floatArrayOf(0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f),
            Illuminant.D50,
            TransferParameters(1.8, 1.0, 0.0, 1 / 16.0, 0.031248),
            id = 11
        )

    /**
     * [RGB][Rgb] color space ACES standardized as SMPTE ST 2065-1:2012.
     * [See details on ACES color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#ACES)
     */
    public val Aces: Rgb =
        Rgb(
            "SMPTE ST 2065-1:2012 ACES",
            floatArrayOf(0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.0770f),
            Illuminant.D60,
            1.0,
            -65504.0f,
            65504.0f,
            id = 12
        )

    /**
     * [RGB][Rgb] color space ACEScg standardized as Academy S-2014-004.
     * [See details on ACEScg color space](https://d.android.com/reference/android/graphics/ColorSpace.Named.html#ACES_CG)
     */
    public val Acescg: Rgb =
        Rgb(
            "Academy S-2014-004 ACEScg",
            floatArrayOf(0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f),
            Illuminant.D60,
            1.0,
            -65504.0f,
            65504.0f,
            id = 13
        )

    /**
     * [XYZ][ColorModel.Xyz] color space CIE XYZ. This color space assumes standard illuminant D50
     * as its white point.
     *
     * ```
     * | Property                | Value                 |
     * |-------------------------|-----------------------|
     * | Name                    | Generic XYZ           |
     * | CIE standard illuminant | [D50][Illuminant.D50] |
     * | Range                   | `[-2.0, 2.0]`         |
     * ```
     */
    public val CieXyz: ColorSpace = Xyz("Generic XYZ", id = 14)

    /**
     * [Lab][ColorModel.Lab] color space CIE L*a*b*. This color space uses CIE XYZ D50 as a profile
     * conversion space.
     *
     * ```
     * | Property                | Value                                                   |
     * |-------------------------|---------------------------------------------------------|
     * | Name                    | Generic L*a*b*                                          |
     * | CIE standard illuminant | [D50][Illuminant.D50]                                   |
     * | Range                   | (L: `[0.0, 100.0]`, a: `[-128, 128]`, b: `[-128, 128]`) |
     * ```
     */
    public val CieLab: ColorSpace = Lab("Generic L*a*b*", id = 15)

    /** This identifies the 'None' color. */
    internal val Unspecified =
        Rgb("None", SrgbPrimaries, Illuminant.D65, NoneTransferParameters, id = 16)

    /**
     * [RGB][Rgb] color space BT.2100 standardized as Hybrid Log Gamma encoding
     *
     * ```
     * | Property                | Value                                                   |
     * |-------------------------|---------------------------------------------------------|
     * | Name                    | Hybrid Log Gamma encoding                               |
     * | CIE standard illuminant | [D65][Illuminant.D65]                                   |
     * | Range                   | `[0.0, 1.0]`                                            |
     * ```
     */
    public val Bt2020Hlg: Rgb =
        Rgb(
            "Hybrid Log Gamma encoding",
            Bt2020Primaries,
            Illuminant.D65,
            null,
            { x -> transferHlgOetf(Bt2020HlgTransferParameters, x) },
            { x -> transferHlgEotf(Bt2020HlgTransferParameters, x) },
            0.0f,
            1.0f,
            Bt2020HlgTransferParameters,
            id = 17
        )

    /**
     * [RGB][Rgb] color space BT.2100 standardized as Perceptual Quantizer encoding
     *
     * ```
     * | Property                | Value                                                   |
     * |-------------------------|---------------------------------------------------------|
     * | Name                    | Perceptual Quantizer encoding                             |
     * | CIE standard illuminant | [D65][Illuminant.D65]                                   |
     * | Range                   | `[0.0, 1.0]`                                            |
     * ```
     */
    public val Bt2020Pq: Rgb =
        Rgb(
            "Perceptual Quantizer encoding",
            Bt2020Primaries,
            Illuminant.D65,
            null,
            { x -> transferSt2048Oetf(Bt2020PqTransferParameters, x) },
            { x -> transferSt2048Eotf(Bt2020PqTransferParameters, x) },
            0.0f,
            1.0f,
            Bt2020PqTransferParameters,
            id = 18
        )

    /**
     * [Lab][ColorModel.Lab] color space Oklab. This color space uses Oklab D65 as a profile
     * conversion space.
     *
     * ```
     * | Property                | Value                                                   |
     * |-------------------------|---------------------------------------------------------|
     * | Name                    | Oklab                                                   |
     * | CIE standard illuminant | [D65][Illuminant.D65]                                   |
     * | Range                   | (L: `[0.0, 1.0]`, a: `[-2, 2]`, b: `[-2, 2]`)           |
     * ```
     */
    public val Oklab: ColorSpace = Oklab("Oklab", id = 19)

    /**
     * Returns a [ColorSpaces] instance of [ColorSpace] that matches the specified RGB to CIE XYZ
     * transform and transfer functions. If no instance can be found, this method returns null.
     *
     * The color transform matrix is assumed to target the CIE XYZ space a [D50][Illuminant.D50]
     * standard illuminant.
     *
     * @param toXYZD50 3x3 column-major transform matrix from RGB to the profile connection space
     *   CIE XYZ as an array of 9 floats, cannot be null
     * @param function Parameters for the transfer functions
     * @return A non-null [ColorSpace] if a match is found, null otherwise
     */
    public fun match(@Size(9) toXYZD50: FloatArray, function: TransferParameters): ColorSpace? {
        for (colorSpace in ColorSpacesArray) {
            if (colorSpace.model == ColorModel.Rgb) {
                val rgb = colorSpace.adapt(Illuminant.D50) as Rgb
                if (
                    (compare(toXYZD50, rgb.transform) && compare(function, rgb.transferParameters))
                ) {
                    return colorSpace
                }
            }
        }

        return null
    }

    internal inline fun getColorSpace(id: Int): ColorSpace = ColorSpacesArray[id]

    /** These MUST be in the order of their IDs */
    internal val ColorSpacesArray =
        arrayOf(
            Srgb,
            LinearSrgb,
            ExtendedSrgb,
            LinearExtendedSrgb,
            Bt709,
            Bt2020,
            DciP3,
            DisplayP3,
            Ntsc1953,
            SmpteC,
            AdobeRgb,
            ProPhotoRgb,
            Aces,
            Acescg,
            CieXyz,
            CieLab,
            Unspecified,
            Bt2020Hlg,
            Bt2020Pq,
            Oklab
        )

    internal fun transferHlgOetf(params: TransferParameters, x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        var absX = x * sign

        // Unpack the transfer params matching skia's packing & invert R, G, and a
        val R = 1.0 / params.a
        val G = 1.0 / params.b
        val a = 1.0 / params.c
        val b = params.d
        val c = params.e
        val K = params.f + 1.0

        absX /= K
        val result =
            if (absX <= 1) {
                R * absX.pow(G)
            } else {
                a * ln(absX - b) + c
            }
        return sign * result
    }

    internal fun transferHlgEotf(params: TransferParameters, x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val absX = x * sign

        // Unpack the transfer params matching skia's packing
        val R = params.a
        val G = params.b
        val a = params.c
        val b = params.d
        val c = params.e
        val K = params.f + 1.0

        val result =
            if (absX * R <= 1) {
                (absX * R).pow(G)
            } else {
                exp((absX - c) * a) + b
            }
        return K * sign * result
    }

    internal fun transferSt2048Oetf(params: TransferParameters, x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val absX = x * sign

        val a = -params.a
        val b = params.d
        val c = 1.0 / params.f
        val d = params.b
        val e = -params.e
        val f = 1.0 / params.c

        val tmp = max(a + b * absX.pow(c), 0.0)
        return sign * (tmp / (d + e * absX.pow(c))).pow(f)
    }

    internal fun transferSt2048Eotf(pq: TransferParameters, x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val absX = x * sign

        val tmp = (pq.a + pq.b * absX.pow(pq.c)).coerceAtLeast(0.0)
        return sign * (tmp / (pq.d + pq.e * absX.pow(pq.c))).pow(pq.f)
    }
}
