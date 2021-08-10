/*
 * Copyright 2021 The Android Open Source Project
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

@file:Suppress("UnstableApiUsage")

package androidx.compose.lint.test

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.compiled
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile
import org.intellij.lang.annotations.Language
import java.util.Locale

/**
 * Common Compose-related bytecode lint stubs used for testing
 */
object Stubs {
    val Color: TestFile = compiledStub(
        filename = "Color.kt",
        filepath = "androidx/compose/ui/graphics",
        checksum = 0x2a148ced,
        source = """
            package androidx.compose.ui.graphics

            inline class Color(val value: ULong) {
                companion object {
                    val Black = Color(0xFF000000)
                    val DarkGray = Color(0xFF444444)
                    val Gray = Color(0xFF888888)
                    val LightGray = Color(0xFFCCCCCC)
                    val White = Color(0xFFFFFFFF)
                    val Red = Color(0xFFFF0000)
                    val Green = Color(0xFF00FF00)
                    val Blue = Color(0xFF0000FF)
                    val Yellow = Color(0xFFFFFF00)
                    val Cyan = Color(0xFF00FFFF)
                    val Magenta = Color(0xFFFF00FF)
                    val Transparent = Color(0x00000000)
                }
            }

            fun Color(color: Long): Color {
                return Color(value = (color.toULong() and 0xffffffffUL) shl 32)
            }

            fun Color(color: Int): Color {
                return Color(value = color.toULong() shl 32)
            }

            fun Color(
                red: Float,
                green: Float,
                blue: Float,
                alpha: Float = 1f,
            ): Color = Color.Black

            fun Color(
                red: Int,
                green: Int,
                blue: Int,
                alpha: Int = 0xFF
            ): Color = Color.Black
        """,
"""
        META-INF/main.kotlin_module:
        H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcolmZiXUpSfmVKhl5yfW5BfnKqX
        m1iSWpSZmCPE4Zyfk19U7F3Cpc4li1OZXlp+vhBbSGpxCVihDIbC0ky99KLE
        gozM5GIhdrCR3iVKDFoMAMec7K6RAAAA
        """,
        """
        androidx/compose/ui/graphics/Color＄Companion.class:
        H4sIAAAAAAAAAJWWXVMcRRSG3579ZFiW5TNACBpcI2BgAWOiIUbDYpBkSZQQ
        YkTFZnaEgdkZanoWkzvKKv0feu+FV6a8sCi883/4NyxP9ywMjl2roYrunvO+
        Tx+mZ84Z/vjr198AXIPFcJV79cB36s8qlt848IVdaTqVnYAf7DqWqFR91w/K
        VVK45/heDoyhtMcPecXl3k7l4faebYU5pBiytxzPCW8zpCYmNwrIIGsijRxD
        Otx1BMNM7WUSLVCaHTtcdLm1Pz1bv7F1f++x2voew/h/b5RDF0MvtyxbiPLp
        PmXroIBuFEwUUWLoo/gSD/aXA/48TjEQQ6ei4voirp+hm6R/Mj0xc+a/EPmH
        GPopXHN2dhPQYAydqYq8GJGj0Qk82XVCO6bO3ZNSFPFKRLzKUKT4ml2P/aXY
        T3Hlfi1yl6P9lwPb9rT7K0URb0TERHTvi27T1t67FJT/zch/lVQKP7Vd1/8m
        JvpiIpIUU4mY2ShH9Tn3tDmkoPxvRf5r9CdTeJXv2F7IY6Q/Rlqaom5E1Dt0
        /KSsB9wTBzwgOSaHYvKcruiFiL7FMDdR2/dD1/Eqe4eNiuOFduBxt7Jkf82b
        blj1PREGTSv0g1V6h+xgYXLDhCGLoa9sxeJWQ6myNl5qNzqRU2DVDnmdh5xi
        RuMwRUXN5JBhYPsUeubIKzpUoz7H2J/HR/2mMWSYRun4yDTyRnSRH86ffJ8a
        Oj6aN2bZYi5vnPyYNUrG2mgpNWLMpn9/wY6PaGD0KyWT8NxIOp8pZcmSb2fp
        UBaznaVTWQrtLF3KUmxn6VaWUjtLj7L0trP0KUt/O8uAsgy2s1xQlqF2lmFl
        GWlnuagso+0sl0rZk2+NbvkAzUz+5IexWUbry/JhzzN6EZBRbY+h/D9aL71A
        jHprWhYxTbLOGPKnLZAi0ZRRXYGh46xnMeRaFUb9mXoMQ+e5uiFC9Sn6PkTF
        TuhZk6cEKvfMfihT+nWyddccz37QbGzbwTrfdinSW/Mt7m7wwJHXrWBhxfPs
        oOpyIWz6uJiP/GZg2XcdqQ2vNb3QadgbjnDIfMfz/JCHlE9gjqowDflTpBV9
        o+iUvqCrCs1M1s3UL8j/TAsDX9KYjYLYorHQWnfApLkHnSoi4RlySy39Aj0/
        JdjsOTZ9xvbq2IEkm9Oygzp2OMnmteyIjr2UZDu07JiOvZxkTS07rmNfT7Kd
        WvaKjp1MsgUtO6Vjp5Nsl5ad0bFzSbaoZed17NtJtlvLXtex7ybZkpa9qWPf
        S7I9/2bp36EMbrfYaZqNVjG8L4uBKWAwCraSydUHuENaCl+pBy+hIjYxDK4S
        fo5tmr+j+CJ5q5tIrWBpBR/SiLtyWF7BR1jZBBO4h/ubGBMwBWoCWYFVgQcC
        nQIFgYcCHwtMCXwiMC+wJtAr8EhgUGBd4IrAY4ERgQ2B6wJPBMYFPhW4KfBU
        yD0/E5j5G4ru30HxCgAA
        """,
        """
        androidx/compose/ui/graphics/Color.class:
        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
        UC9ZO4QPAAA=
        """,
        """
        androidx/compose/ui/graphics/ColorKt.class:
        H4sIAAAAAAAAAJVUz28bRRT+Zn/a2026ae202dSpk7hglyR2UkgAN6EhUiSn
        LpUo6SUHNF4v7ibrXWt3XZULREj8EVy5c+GALA4oKjdO/EWUN5uVnaZCLSvN
        vB8z73vfmzezf/3z+x8APsQuQ4UH3Sj0ui/qTtgfhLFbH3r1XsQHzzwnru+F
        fhg9THQwBuuYP+d1nwe9+uPOseuQV2ZQ0y0MSvWgdsBgnoSJ7wX1w3YY9HTo
        FOaEQZxEQycJo1WvP/BN5KEZyMFQiQRe0cew9HYaOqYNXIVBOZ3znOxA5G2J
        vKzFoFf36RNWfo9AeOCFAcNa++3QlfH+polZ3MhTnpsMK/8nUodNxfbc5HOf
        Oyerje7W1w+PDxnkau3AxC2UDMxjgezI7RLffSqjF7kuMVQ6/tAlk/uDZ5xh
        6hy4637Dh37CYKdltdqXj78pcFUUDUh4X1Tfok9Ub6fafwbURMAHDDPtrFeP
        3IR3ecKbDFL/uUw9YWJSieWJUCTyv/CE1iCtu87w99npgnF2akg3pVRYuiHl
        VJI5khLJPLnthkWT1GAbmiWRlEkqmVRTmctJlia0P0fs7PTlzxoB2bOTqBzJ
        /GTNuIyYzxDzGWJ+jHgh6splRHOyNvXyB0khynOirg16DO9wV+iUwJBL9bUT
        6o+yF3apfVfbXuB+Mex33Ogr3vHJc60dOtx/yiNP2Jlz6klC1+MRH2S28SQc
        Ro677wlj7sthkHh996kXe7S6GwRhwhO6XDHWqWmKeC/QMEdd1IhEk6wOeXWS
        Kwu78qZSHuHKbgHmliJvauVZ5XthW+VNMtVyUfmWzBGsX0RTcZ/maynmLAwa
        No0qjW3ymIROmJihHUjzXc/ytSFDvFvbvi9vz/9IgA+s8g6lvl16A30a4tLY
        hG6n+BNkm5ALGXIxQ36cVaj/irnfcHsCo6UhS2n43fMtKGMxPQyd/Ivpuo5l
        0qRUq5AmW3ncwXsZeJPiNFFwUbFy3/0E1dxfWq7cGaF6nmeHZhnMuMC7RL+o
        ReJeSlnW3o1l7RLLwphlYcyyMGZZyFjezcC3M5aFjKU+g1c79vyt0ggrr/E0
        L/CsEM8qnWuFlj9LN32KByQPCXSVmK8dQW6h3kKDZqy3sIF7Lfr5f3QEFmMT
        W0eYjqHG+DhGOcYnMZZiLMeoxCjGmEk912PUUkX7F4jgH2k8BgAA
        """
    )

    val Composable: TestFile = compiledStub(
        filename = "Composable.kt",
        filepath = "androidx/compose/runtime",
        checksum = 0x12c49724,
        source = """
        package androidx.compose.runtime

        @MustBeDocumented
        @Retention(AnnotationRetention.BINARY)
        @Target(
            AnnotationTarget.FUNCTION,
            AnnotationTarget.TYPE,
            AnnotationTarget.TYPE_PARAMETER,
            AnnotationTarget.PROPERTY_GETTER
        )
        annotation class Composable
        """,
"""
        META-INF/main.kotlin_module:
        H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcYlkZiXUpSfmVKhl5yfW5BfnKpX
        VJpXkpmbKsQVlJqbmpuUWuRdwqXJJYyhrjRTSMgZwk7xzU/JTMsEK+XjYilJ
        LS4RYgsBkt4lSgxaDACMRj6sewAAAA==
        """,
        """
        androidx/compose/runtime/Composable.class:
        H4sIAAAAAAAAAI1STW/aQBB9ayBQ2ibQT0ia5pu0PdRp1FtPQJwWiS8ZJxLi
        EG3sVeRg7AivaXLjUKn/qYcK9dgfVXUWVKCSpdaW3s7OvNnZeTs/f337DuA9
        3jDscd8ZBq5zq9vB4CYIhT6MfOkOhF6d7vmlJ9JgDLlrPuK6x/0rvXV5LWyZ
        RoJha+Hlvh9ILt3A18tzM40Uw369H0jP9ZcpjSiUFXES2NFA+FI4Hxg2Y2im
        kBQmi+KpEfciwXAYw1tUXM5YqdSaZbPLsB6TYvHhlZDEWuWeF3wWzswRxt93
        UWCelzk9a1atWqvJkLS6bYNOUstFu2yWG4ZlmAxrbbPVNkyre/HRsKaenXqs
        Yn8JsR3PWe6s9A9KO/Bc+06JVq2XOx0lbmzCvJnd+LjhCXUt6+5GKD2prU+t
        E2p92uhZh3rO/xGrISR3uOTE0wajBA0YU0Dvz/rkunXV7ogs5x1DcTLOZLWC
        ltVyG5kfX7XCZHysHbHKZKwIxwwH9f8YTCoFhocLx9u+ZMh2gmhoi1PXo2Ep
        mrOsczd0ibB4xrBElZCk/BWoT8OrKR7iNa1fkKYfyFD8nkAW9/FAleohKbCK
        NQU5BXkFj/CYuE9m3Kd4hufK7CEhUEBRQV7BOjaQwgvy17BZw0tCbCnYrmEH
        uz2wEHvY70ELcRCi9BuuoX9IqAMAAA==
        """
    )

    val Modifier: TestFile = compiledStub(
        filename = "Modifier.kt",
        filepath = "androidx/compose/ui",
        checksum = 0xe49bcfc1,
        source = """
        package androidx.compose.ui

        @Suppress("ModifierFactoryExtensionFunction")
        interface Modifier {
            infix fun then(other: Modifier): Modifier =
                if (other === Modifier) this else CombinedModifier(this, other)

            interface Element : Modifier

            companion object : Modifier {
                override infix fun then(other: Modifier): Modifier = other
            }
        }

        class CombinedModifier(
            private val outer: Modifier,
            private val inner: Modifier
        ) : Modifier
        """,
"""
        META-INF/main.kotlin_module:
        H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcYlkZiXUpSfmVKhl5yfW5BfnKpX
        VJpXkpmbKsQVlJqbmpuUWuRdwqXJJYyhrjRTSMgZwk7xzU/JTMsEK+XjYilJ
        LS4RYgsBkt4lSgxaDACMRj6sewAAAA==
        """,
        """
        androidx/compose/ui/CombinedModifier.class:
        H4sIAAAAAAAAAKVTWU8TURT+7nRlKFJGyw6iVOmCDCAusQajGJMmBQ0QYoIv
        t+0FbjudITPThkfiT/EX6AOR+GAIj/4o47ltKVpIeTBNz/Lds58zv37/+Alg
        Bc8Zktwuu44sH5klp3boeMKsS3PNqRWlLcrrTlnuSeFGwBjiFd7gpsXtffN9
        sSJKfgQBhsnr/C/9Qgzhl9KW/irDq1Shl3Gu92t6h2G24Lj7ZkX4RZdL2zO5
        bTs+96VD8objb9QtK8cQcuq+cKPQGaarjm9J26w0aqa0CbW5ZeZt3yVvWfIi
        iDEkSgeiVG27f+AurwkyZJhLFbo7zv2FbKkg+1RWDLcwqGMAcUotbVulNhgC
        KfUUwR0dQSSolN7txaBjpA8aRmMwWtI4Q9A/kB7Dw2t9u7eUa9oLm2H1hkmn
        ez8zpHu9J9+KPV63/Hzt0KIRzjB8+r/N3jSZadzXcQ+zarXUIO1mqNBe7Lrw
        eZn7nGrWao0AXTVThM6OVQk6kkpbJKm8xLB9dhzTtVGt9Y8GRs+Ol7VF9iZh
        hOPaOEnEA4qffwlr8eDmrZb28fxzUCH62TFBWhekYi8z9F/Uu1D1GSY267Yv
        ayJvN6Qni5Z4fXmptKU1pywYBgu0vY16rSjcbU42DEbBKXFrh7tS6W0w2R2r
        c6P/BNW3nLpbEu+k8hlr++xcyY4luqwg6LMkbqijI3mJhhQm3kfcUOfXhWnE
        I4gSXyZNEFdjncga/acYmjduE818x3Ame4KxzPwJJr41nR43wwURQ5x+Q0iQ
        Nk7SCuEzrRCYxBTQlFqlKEkVoOEJyQNau4ILOo275KiqeEEmmqoxkz1F8msn
        YbgZpJVkuGXRSRLGgyu9qTQR1mn0aZMu4lkzBX16CGFuF4E8UnmkiSKjSDaP
        eTzaBfOwAHMXIQ9THkY86B4MD9E/kXas0GMFAAA=
        """,
        """
        androidx/compose/ui/Modifier＄Companion.class:
        H4sIAAAAAAAAAI1SXU8TQRQ9s1u6y1JkQYHyIX5QoYCyQHwwlpBgo7GmVCOE
        xPA03Y4w7XbW7G4bHnnyJ/gD/AUSHzCaGMKjP8p4p1QUTMCHuTP3zDn33rl3
        fvz8+h3AQ6wwzHBVi0JZ2/f8sPkujIXXkt5GWJNvpYhyRcK4kqGywBjcOm9z
        L+Bq13tZrQs/sWAyTF4WwUIPQ3pVKpmsMZj5ue0MLNgOUuhlSCV7MmbIl/+v
        iEJHIRTD2uWSwtzl1wzT5TDa9eoiqUZcqtjjSoUJTyhJ7FXCpNIKAmL1hJQu
        suEyTDXCJJDKq7ebnlSJiBQPvJJKIlJLP7YwxDDs7wm/0ZW/4hFvCiIyzObL
        FztX+AvZ1EF2C7o1NzDs4DpGKN9VL7BX/aDTVgeG7qWTy5Uqm1vrleLTDCaQ
        6SV4kmGw3C18QyS8xhNOUqPZNmn+TBsaD2sQtC+1t0Sn2jLDo+ODjGNkjdNl
        m/bJezN7fLBiLLEnlm2cfEwbrvFi0DXHCVlJuym9Pz/5kNJ6+lW9ZzNj6Ptd
        9GIjoQEWw5pgGChLJSqtZlVEW7waEDJUDn0ebPNIar8LTrxuqUQ2RUm1ZSwJ
        Wv8zJ4bcxduznp+jZUpKiagY8DgW5DqbYSvyxTOpE4x1Q2z/Ex7L1MAU6PeC
        uVndUeqPSRh9X0LnyfNoZ7qH80dwDulgYIFsugP24T7ZzCmBPC1n6Mc1CqLF
        q8Q2aLcXhga/YHTh0zl9mvhaP3LK6er1yUWW7h90eQO0L9KyWNexMXZW32hH
        TKV8g/HmCOOfcfOwA5hUOuAQzaAoeZIsdbLP0aOBx4RPUc23dmCWcLuEO2Rx
        V5vpEnK4twMWYwazO+iJkYmRjWHH6I/h/gJ6Cp4UWgQAAA==
        """,
        """
        androidx/compose/ui/Modifier＄DefaultImpls.class:
        H4sIAAAAAAAAAKVSXU8TQRQ9swW2LUVKEbCCoFKlLcqi8a1GYzAmG9tqrGli
        9GW6HWDa3RmyO9vwi/TV+KLRxPjsjzLehRUQTX1wk71zz/04M/fj+4/PXwHc
        w12GGlf9UMv+oePp4EBHwoml09J9uStFWHksdnnsGzc48CMbjKE44CPu+Fzt
        Oc96A+EZGxmGCbMvFMObanMcW2O8tzbezbDe1OGeMxCmF3KpIocrpQ03UpPe
        1qYd+37j6C0yyiLLsDrUxpfKGYwCRyojQsV9x1UmpGTpUT15hgVvX3jDNPs5
        D3kgKJBho9o8X2njjKWTkOw1at0CCpjJYxoXGCY1tSHMosiwMq4UGyWG3A45
        uKLHM4xvW+UkslHARSzkMI9Fhsrfcii0J5Xon151iWHqvlTSPGB4+H/zoWIv
        YzmPMlaouf+a1lwzbX9LGN7nhpPNCkYZWjyWiEkGNkwU2h/rUCbaNkPh7MYx
        TP8i3BoaGu2O7guGmY7h3rDFD17ynk94tkklt+OgJ8LUUmpqj/tdHsoEp8bl
        F7EyMhCuGslIkunR6f5QO897T5bht7CCq5QId3weRYJgvqPj0BNPZHJBOaXo
        /kGPO7AwgeSjyWMSU8igRkiQ1aKzUi/lPmF2szSXyA9Yegu7/g6zX1B+Vd/8
        iCvfMP8+6RXqJG1YS09tWoNNAlNEWSDTLdIXj8mQxerRZRUUSWO4ncbZdG7R
        P2Ol4Fhm4JDME7KIdIMGvE3YQpXejSOCNYq5+hoZF9dcXCeJdZfYb7i4+RNB
        PoFzTQQAAA==
        """,
        """
        androidx/compose/ui/Modifier＄Element＄DefaultImpls.class:
        H4sIAAAAAAAAAKVSS2/TQBD+Ni8nqUvTlBRKS3k0tHlATSVuOaECkiU3IIpy
        gcvG2aab2LuVvYn6szhWHBBnfhRinBpoixQiYcmz8/hmZveb+f7jy1cAL/Cc
        4YCrQaTl4NzxdXimY+FMpHOkB/JEiqj+OhChUKb+SpzwSWDc8CyILTCGyohP
        uRNwNXTe9kfCNxayDDlzKhTDoOEtUrUzF9Vpzg8z7Hg6GjojYfoRlyp2uFLa
        cCM16V1tupMg6MzuJOMiigzbY20CqZzRNHSkMiJSPHBcZSJKlj69q8xQ80+F
        P06z3/GIh4KADHsN7+aLO1c8x0mRYafZs2FjuYwl3GLIa6IjKqLCsDXvKRaq
        DM25jF3n/zbDp/kU/x+1NvJYL6OGOwy7i42S2P3XuFa9lP8jYfiAG06+TDjN
        0iayROQZ2DhRaJEy5zLRaD/ri7S3sMNgXyWJwUpjDEu/4PtjsnKHeiAYVjyp
        RHcS9kX0gfcD8lQ97fOgxyOZ2Klz8/1EGRkKV01lLMn18s+S0eVuRn9vzDWY
        7SolosOAx7Egs3ysJ5Ev3sikwUZaovdXeRwggxySj6FEIykgiwZZLvkzdNZa
        1dIFVtrVVZKtb1hrX+DuZwpk0CRZoDSbElukr18moIiNWcEaKrhH8XaKs+h8
        Sv9yJjUuZRbPSFZn/ep4Qsl1rGG3UML+rM0eHDq3Cb1J2K2PyLq472KbJB64
        eIhHLh7/BG04LjBuBAAA
        """,
        """
        androidx/compose/ui/Modifier＄Element.class:
        H4sIAAAAAAAAAI1QTU8CMRB9s6ssXyogKqjxRDy6QLx5Mn4km0BMNPHCqbDF
        VHZbst0lHPldHgxnf5RxNtHEE6HJvL5586ad9uv74xPANc4IHaHDxKhw6U9M
        PDdW+pnyhyZUUyWTzkMkY6lTD0SovYuF8COh3/yn8bucsOoSzjf1e9gl1Acz
        k0ZK+0OZilCk4obgxAuXJ6Ac2EIzlpYqz7rMwh6huV4Vy07LyaM4ba1XfadL
        ea1PuBxsMzRfc7HRyIbeNgd17uVUZFEaxPPIejgkVP8rBO/XSKj89V7NOKsG
        WsvkLhLWSraVX0yWTOSjiiSh/ZzpVMXyVVk1juSt1iYVqTLaFviV2AGhgHy5
        aDE2WGtyHKHNWMBxocQMzE9wynuP/fzbKI7gBigFKDOikkM1wB72RyCLA9RG
        cCzqFo0fJIqRGAUCAAA=
        """,
        """
        androidx/compose/ui/Modifier.class:
        H4sIAAAAAAAAAIVSS2/TQBD+ZuPESRogoTzSB6XQUBJeLhVcKFSqQhFGbUAU
        9dLTNtmUbZ115d1EPeZX8D+AGwcUceRHIcZVoRSkYMvzzXwzOzOe2e8/vnwF
        8Aj3CbPSdJJYd46Cdtw7jK0K+jrYjDu6q1XigwjlfTmQQSTNXvB6d1+1nY8M
        wXPvlSGs1jfGJVhpjHcTFjbiZC/YV243kdrYQBoTO+l0zHordq1+FHFU/mk7
        0ka7VUKm3tgmLI5LW2syJw3n8FEkFGu1sLX1bq3VXCeM7/f05EoJJZwrYALn
        CYXfdAnllBWoECobB7HjtoJN5WRHOsmNit4gw6OlVGQJdMDUkU6tJdY6DwmP
        R8NSUVRFUZRHw6LIe/ludTSc95bFEj0RXvZVpSymxdJouJwrZ46Vl98+eOnh
        ZcLc/8YJQjbm1SQEfz1SPWUcoTb2l0/CfNwgNMZGPldd2Y9c2DuMrI8FQulP
        hjDxK/LBAVededs3TvdUaAba6t1IrZ2ulnv62/tGJrKnnErOhHnNuKO4UGiM
        SpqRtFYxW9yK+0lbvdAR+6ZOMm3/UyXHU4OXrgNT6d4Yb7OVY/QZBU81y5Y4
        w9b58+nEyPNbYL3B+iRj+vifcOEzLn48NjK4w3Ka8Sbnmc+lF0ZwrSnUGG8x
        3k0rYRH3GJ9xikmueWkHmRCXQ1xhiaupqIYcO70DspjB7A5yFtcs5iyyFtct
        yhbzPwFNAZX3twMAAA==
        """
    )

    val Remember: TestFile = compiledStub(
        filename = "Remember.kt",
        filepath = "androidx/compose/runtime",
        checksum = 0xc78323f1,
        source = """
        package androidx.compose.runtime

        import androidx.compose.runtime.Composable

        @Composable
        inline fun <T> remember(calculation: () -> T): T = calculation()

        @Composable
        inline fun <T, V1> remember(
            v1: V1,
            calculation: () -> T
        ): T = calculation()

        @Composable
        inline fun <T, V1, V2> remember(
            v1: V1,
            v2: V2,
            calculation: () -> T
        ): T = calculation()

        @Composable
        inline fun <T, V1, V2, V3> remember(
            v1: V1,
            v2: V2,
            v3: V3,
            calculation: () -> T
        ): T = calculation()

        @Composable
        inline fun <V> remember(
            vararg inputs: Any?,
            calculation: () -> V
        ): V = calculation()
        """,
"""
        META-INF/main.kotlin_module:
        H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcYlkZiXUpSfmVKhl5yfW5BfnKpX
        VJpXkpmbKsQVlJqbmpuUWuRdwqXJJYyhrjRTSMgZwk7xzU/JTMsEK+XjYilJ
        LS4RYgsBkt4lSgxaDACMRj6sewAAAA==
        """,
        """
        androidx/compose/runtime/RememberKt.class:
        H4sIAAAAAAAAAK1WXVPbRhQ9K38J29jCfJSYJiFgyndkG5q2MZBSWhpPCekE
        j9opT8JWqMCWMlrZk0emL33pH+hrf0Ef0z50GPrWH9XpXVlgg4UJTTzW3qvr
        u+ecu3el9T///vkXgFV8xTCtWzXHNmuv1ardeGVzQ3Walms2DPWF0TAaB4bz
        jRsDY1CO9Jau1nXrUH1+cGRUKRpikB0/i2F1bufYduumpR61GurLplV1Tdvi
        6rbv5UvzO1cxSgyba5XHvfGNm8DWFiuV0kZpnkaGmZ1rq9jy7vWDukF50zu2
        c6geGe6Bo5uEpluW7ept5F3b3W3W65SVqOr1arPuxWXEGe51STEt13Asva6W
        LdchDLPKY0gyjFZ/NKrHPsi3uqM3DFesyuxcb3FdkT0Bclia15JIIR3HIJTL
        fAGlx5BhiJpWyz42GEbmApY1iRGMJjCMMYbBnJl7mev0iZUZJm9qFcN2kPD/
        0+AfAhusFQK7XtEKN7Fc6rzUKjBkgmi/77/w71IRf/uKtOK1ZVa04i1LLTIc
        vV1V76fOX96xTm2lb/EVbeWWC7DC8PXc/nuqrrKmBcq7PT6p1DyVWsl7MF81
        XS5jlmE4AIth6BzumeHqNd3VRW2NVoheyUwMEXpGj4UjUfy1Kbw8ebUCY+7p
        yVhckkNxaVwim4xLyhBdpyfx05PsDNms9JRNheXTE4UVk4qUlTPhDIXyoadn
        P8t/v2GnJ2e/RSUlnF25nEyGKZFiVIlSMNJvaiy7GTSVRkmRLwCiygBZuR9Q
        PPv8eiAaQ0qiBy6qJMkm+sEOZtfbsKk2bKo4pqSzyYwss0x4nOWH8sqUZ7tA
        UldBMmc/SbF4RD77tZhnYu3p0WMV8abxW9f9ppS0ghiKYqAdyjRGjUTi/Px8
        eOwyhLfsGr2s0zumZew2RbgiziSBaNNxo+mOKe794MCeeWjpbtMhf+JF+yQr
        Wy2Tm/TzZufQYshd/fXi6LmUFt+zm07V2DYF+h1/jtaDhwIkhCE+EdyhK0p3
        63S3RXGJbHohk3iDodDawh/4gOF3sUmxQWOUKpYRwxPyxygm/HGCYGIS4siS
        /dzLjmHzIh/4gq4YrRkGyBGMEz7jM0oVOz+92GZcXwxkHPQYJyn1nNGTibu4
        55XR5mY+94c93AMivcN+32f/zl+H9FKbfWMpkH3EY1+g1HP20BX2B+R11kDy
        dUz26EiEvAkdJVO+kgNKj5BVlttKHoWXA6QMUGlPvL90EfLbUoR+5UKKciFF
        QY48yfOEqJAvarpH1GB7K3TLmvFl7fntGV3IzJGsfk1KUS3nTUp1NWkUs5j3
        4EcvNemjXh2Sr6A9SrQdxbiGL8nWKLpAyhb3ESpjqYxlGvGwDBX5Mm3o4j4Y
        xwpW9zHMEeH4mCPO8YgjyvEJx12OCY5POR5w3Of4jCPHMcXxmGOWo+R9Z/4D
        oSNh5zILAAA=
        """
    )

    val MutableState: TestFile = compiledStub(
        filename = "MutableState.kt",
        filepath = "androidx/compose/runtime",
        checksum = 0xbd322309,
        source = """
        package androidx.compose.runtime

        fun <T> mutableStateOf(value: T) = MutableState<T>()

        class MutableState<T>

        fun <T> mutableStateListOf() = SnapshotStateList<T>()
        class SnapshotStateList<T>

        fun <K, V> mutableStateMapOf() = SnapshotStateMap<K, V>()
        class SnapshotStateMap<K, V>
        """,
"""
        META-INF/main.kotlin_module:
        H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3ApcUlkZiXUpSfmVKhl5yfW5BfnKpX
        VJpXkpmbKsTnW1qSmJSTGlySWJLqXcJlzqWIS61ecWJZKkitkFBQam5qblJq
        UTBUBKiRl4ulJLW4RIjVLT/fu0SJQYsBAFB+NmSMAAAA
        """,
        """
        androidx/compose/runtime/MutableState.class:
        H4sIAAAAAAAAAI1QTUsbQRh+ZjbZ6Jrq+tXG2tpT8ePgqghCK0JbKATWFpqQ
        S06T7KBjkhnZmRWP+1v8B54ED7L06I8qfSd6KO2lc3je93nm4f16/HX/AOAQ
        7xjeC53lRmXXydBMLo2VSV5opyYyOS2cGIxlxwknG2AMW8fdD+mFuBLJWOiz
        5PvgQg7dx5N/JYb4b62BGkN4rLRyJwzB1naviRCNCHXMMNTcubIMm+l/DUP1
        F9ORcWOlk1PpRCacII1PrgLainmoM7ARSdfKsz3Ksn1aoCqbEW/xqCojHhNU
        Zasqd2ozVRmzA77HP9d/3oQ8Drz/gEp0GdXDwp/Nd0eO5v1iMkkfqdLyWzEZ
        yLzrDQxLqRmKcU/kyvNncbajzrRwRU551DFFPpRflf9Y+/G0XU9ZRc5PWhtq
        oYy22Aen0/hHY/hLEa4RS6acFty5w+wtJRyvCcOpGGKdsPlkQIQ5igHeTF0B
        3k5jCxsUj8jTJM+LPoI25ttYIETsYbGNJSz3wSxWsNpHzWLO4qXFK4vGb2l1
        Fhc6AgAA
        """,
        """
        androidx/compose/runtime/MutableStateKt.class:
        H4sIAAAAAAAAAKVUXU8TQRQ9s223ZUG6VBFaFBBEWr624MeDbUiMidq0hcQ2
        TQwPZtquuNDukp1pwyO/xV/gm4kmhvjojzLeaResVE2Jm+y9d+6cc+7e2b37
        /cfnrwAe4QnDKnebvuc0T62G1z7xhG35HVc6bdsqdySvt+yK5NIuyigYg3nE
        u9xqcffQ2q8f2Q3Khhgm2wPI/XcMuXTpKjCXKY1UKMfwMl99OszfTVero4rk
        CbpLSsslzz+0jmxZ97njCou7rkf7jkfxnif3Oq0WoVZG0oxijEHPO64jdxlC
        6UxtAuOYMGDgBkOky1sdmyEx/OCUHDyfkiOkOqOt9D+aqbj8RLz35CWDVAp/
        OZbryFwczNrolChuGZhWPU4NtlHmJ6qLzZHLE4EKv80X/9BE7b8aI+V8tZir
        1nqtZUYmRZEyMNfrrHTsyZbjWmVb8iaXnHS0djdEM8KUiTCwYxVolD91VJSl
        qLnNcHp+ljLOzwxtVjO0mBb40MU6lTAJEEuEE9orLcuWwrHzM1NLzZuh1GQv
        GelZPRvub0XIMVNPLZjRS1YsYO3o5liKEt8+6JppqPo7DBvX+4hYlWH9Wm+M
        Femu0X9i5PmND663jiVD+LnXpOGIlxzX3uu067ZfVQA1Ll6Dt2rcd9Q6SI5V
        nEOXy45P8dzrfo2C23WEQ9vPfo0wg1HxOn7DfuEoWjKA1oaA2IaGMNQVQhIR
        6OQtWu1QnpGPfYHx5hMmP6pXjCxZvZcPExOY6GMQh9njKEwUDwNUjHwSU0gE
        mou0i77mNGneVppsQDMWKNy8ojCD2WGFOVK4c1VhPFBI/qag0e9c2S08Jl+i
        7F3qdP4AoQIWClgki3sFLGG5gPtYOQATeIDVA4wLTAmkBTICMwJrAusCGwIR
        gU2BuID+E+SdSmEvBgAA
        """,
        """
        androidx/compose/runtime/SnapshotStateList.class:
        H4sIAAAAAAAAAI1QTUsbQRh+ZjYfZo111bbGautVg7gqhUIrQhUKgbUFE3LJ
        aZIddEwyIzuz4nF/i/+gp0IPZemxP6r0ndWTvTiH532fZx7erz9/f/4C8B7b
        DF2h08yo9C6emPmNsTLOcu3UXMZ9LW7slXF9J5xMlHVNMIad48HH5Frcingm
        9GX8bXwtJ+7Tyf8SQ/RUa6LG0DhWWrkThmBnd9hGA80QdSww1NyVsgx7yfMn
        oiYrydS4mdLxuXQiFU6Qxue3Ae3HPNQZ2JSkO+XZAWXpIW1RFu2Qd3hYFiGP
        CMqiUxbd2kJZROyIH/DT+u/7Bo8C7z+iEgNG9bB8njsxnslqgP2po6HPTCrp
        I1Fafs3nY5kNvIFhNTETMRuKTHn+KLb66lILl2eUh32TZxP5RfmPjYuHFYfK
        KnJ+1tpQC2W0xSE43cc/GsOfi3CDWFxxWrD7A63vlHC8IWxUYgubhO0HA0Is
        UgywVbkCvK1iB+8ofiBPmzxLIwQ9vOhhmRCRh5UeVrE2ArN4iVcj1CwWLV5b
        rFs0/wH+GL11RAIAAA==
        """,
        """
        androidx/compose/runtime/SnapshotStateMap.class:
        H4sIAAAAAAAAAI1QTW8TMRB99uar29BuWz5SvsqRFoltK04lqgRISFE3IBG0
        l5ycrNW6Sexo7a163N/Sf8AJqYdqxZEfVXW8cAIOWJo3M2+exzP+eXt9A+AN
        XjDsCp3lRmWX8dQslsbKOC+0UwsZj7RY2jPjRk44ORTLNhhDv39ylJyLCxHP
        hT6NP0/O5dS9Tf/BHf9NMUR/cm00GFp9pZU7Zghe7qZdtNAO0USHoeHOlGV4
        lfz3kPTGRjIzbq50PJROZMIJ4vjiIqCNmYcmA5sRdal8tk9RdsAQV+VayHs8
        5B2yqCrDquxV5V6jU5URI8cifsj3g/fNH1ctHjX8tUPqdEKWMmqN9WHhxGQu
        61FezxyN/8FkkgqJ0vJTsZjI/KsXMGwmZirmqciVz3+TKyN1qoUrcorDkSny
        qfyofGH7y69lU2UVKd9pbegJZbTFATj9lD80hv84wseUxXVOu+59x8o3Cjie
        ELZqsounNdYChFglH+BZrQrwvPbb2CF/RJouae6NEQywNsA6ISIPGwNsYmsM
        ZnEfD8ZoWqxaPLR4ZNGzaN8Bn285EWQCAAA=
        """
    )
}

/**
 * Utility for creating a [kotlin] and corresponding [compiled] stub, to try and make it easier to
 * configure everything correctly.
 *
 * @param filename name of the Kotlin source file, with extension - e.g. "Test.kt". These should
 * be unique across a test.
 * @param filepath directory structure matching the package name of the Kotlin source file. E.g.
 * if the source has `package foo.bar`, this should be `foo/bar`. If this does _not_ match, lint
 * will not be able to match the generated classes with the source file, and so won't print them
 * to console.
 * @param source Kotlin source for the bytecode
 * @param bytecode generated bytecode that will be used in tests. Leave empty to generate the
 * bytecode for [source].
 *
 * @return a pair of kotlin test file, to compiled test file
 */
fun kotlinAndCompiledStub(
    filename: String,
    filepath: String,
    checksum: Long,
    @Language("kotlin") source: String,
    vararg bytecode: String
): KotlinAndCompiledStub {
    val filenameWithoutExtension = filename.substringBefore(".").lowercase(Locale.ROOT)
    val kotlin = kotlin(source).to("$filepath/$filename")
    val compiled = compiled(
        "libs/$filenameWithoutExtension.jar",
        kotlin,
        checksum,
        *bytecode
    )
    return KotlinAndCompiledStub(kotlin, compiled)
}

class KotlinAndCompiledStub(
    val kotlin: TestFile,
    val compiled: TestFile
)

/**
 * Utility for creating a [compiled] stub, to try and make it easier to configure everything
 * correctly.
 *
 * @param filename name of the Kotlin source file, with extension - e.g. "Test.kt". These should
 * be unique across a test.
 * @param filepath directory structure matching the package name of the Kotlin source file. E.g.
 * if the source has `package foo.bar`, this should be `foo/bar`. If this does _not_ match, lint
 * will not be able to match the generated classes with the source file, and so won't print them
 * to console.
 * @param source Kotlin source for the bytecode
 * @param bytecode generated bytecode that will be used in tests. Leave empty to generate the
 * bytecode for [source].
 */
fun compiledStub(
    filename: String,
    filepath: String,
    checksum: Long,
    @Language("kotlin") source: String,
    vararg bytecode: String
): TestFile = kotlinAndCompiledStub(filename, filepath, checksum, source, *bytecode).compiled
