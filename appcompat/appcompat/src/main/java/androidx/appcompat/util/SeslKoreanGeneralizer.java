/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.appcompat.util;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


/*
 * Original code by Samsung, all rights reserved to the original author.
 */
public class SeslKoreanGeneralizer {
    public static final Pattern HAS_JOSA_PATTERN = Pattern.compile("(?s)(.*)\\((.+)\\)(.*)");
    @NonNull
    public Map<String, Pair<String, String>> JOSA_KOREAN_MAP = new HashMap<>() {
        {
            put("은(는)", new Pair<>("은", "는"));
            put("(은)는", new Pair<>("은", "는"));
            put("이(가)", new Pair<>("이", "가"));
            put("(이)가", new Pair<>("이", "가"));
            put("을(를)", new Pair<>("을", "를"));
            put("(을)를", new Pair<>("을", "를"));
            put("와(과)", new Pair<>("과", "와"));
            put("(와)과", new Pair<>("과", "와"));
            put("아(야)", new Pair<>("아", "야"));
            put("(아)야", new Pair<>("아", "야"));
            put("(이)여", new Pair<>("이여", "여"));
            put("(으)로", new Pair<>("으로", "로"));
            put("(이)라", new Pair<>("이라", "라"));
            put("(이에)예", new Pair<>("이에", "예"));
            put("이에(예)", new Pair<>("이에", "예"));
            put("(이었)였", new Pair<>("이었", "였"));
            put("이었(였)", new Pair<>("이었", "였"));
            put("(이)네", new Pair<>("이네", "네"));
        }
    };

    public final Map<Character, Pair<Boolean, Boolean>> PRONOUNCEABLE_SYMBOLS = new HashMap<>() {
        {
            put('0', new Pair<>(true, false));
            put('1', new Pair<>(true, true));
            put('2', new Pair<>(false, false));
            put('3', new Pair<>(true, false));
            put('4', new Pair<>(false, false));
            put('5', new Pair<>(false, false));
            put('6', new Pair<>(true, false));
            put('7', new Pair<>(true, true));
            put('8', new Pair<>(true, true));
            put('9', new Pair<>(false, false));
            put('%', new Pair<>(false, false));
            put((char) 65285, new Pair<>(false, false));
            put('$', new Pair<>(false, false));
            put('#', new Pair<>(true, false));
            put((char) 8451, new Pair<>(false, false));
            put((char) 8457, new Pair<>(false, false));
            put((char) 13221, new Pair<>(false, false));
            put('+', new Pair<>(false, false));
            put((char) 176, new Pair<>(false, false));
            put((char) 186, new Pair<>(false, false));
            put((char) 13252, new Pair<>(false, false));
            put((char) 13206, new Pair<>(false, false));
            put((char) 8467, new Pair<>(false, false));
            put((char) 13256, new Pair<>(true, true));
        }
    };

    public String naturalizeText(String str) {
        return naturalize(str);
    }

    public final String naturalize(String str) {
        if (str.isEmpty()) {
            return "";
        }

        if (!hasJosaInString(str)) {
            return str;
        }

        StringBuilder sb = new StringBuilder(str.length());
        int i = 0;
        char previousChar = 0;

        while (i < str.length()) {
            String substring = str.substring(i);
            String josaPattern = null;

            for (String key : JOSA_KOREAN_MAP.keySet()) {
                if (substring.startsWith(key)) {
                    josaPattern = key;
                    break;
                }
            }

            if (josaPattern == null) {
                sb.append(str.charAt(i));
                previousChar = str.charAt(i);
                i++;
                continue;
            }

            String josaFirst = JOSA_KOREAN_MAP.get(josaPattern).first;
            String josaSecond = JOSA_KOREAN_MAP.get(josaPattern).second;

            boolean isEulRo = josaPattern.equals("(으)로");
            Boolean endsWithJongSung = checkIfEndsWithKoreanJongSung(previousChar, isEulRo);
            if (endsWithJongSung == null) {
                endsWithJongSung = checkIfEndsWithPronounceableSymbols(previousChar, isEulRo);
            }

            String josaToAppend = endsWithJongSung != null && !endsWithJongSung ? josaSecond : josaFirst;
            sb.append(josaToAppend);

            previousChar = josaToAppend.charAt(josaToAppend.length() - 1);
            i += josaPattern.length();
        }

        return sb.toString();
    }

    public final Boolean checkIfEndsWithKoreanJongSung(int i, boolean isEulRo) {
        if (i < 44032 || i > 55203) {
            return null;
        }
        int jongSungIndex = (i - 44032) % 28;
        if (isEulRo && (jongSungIndex == 0 || jongSungIndex == 8)) {
            jongSungIndex = 0;
        }
        return jongSungIndex > 0;
    }

    public final boolean hasJosaInString(String str) {
        return HAS_JOSA_PATTERN.matcher(str).matches();
    }

    public final Boolean checkIfEndsWithPronounceableSymbols(char key, boolean isEulRo) {
        Pair<Boolean, Boolean> pair = PRONOUNCEABLE_SYMBOLS.get(key);
        if (pair != null) {
            boolean firstValue = pair.first;
            if (pair.second && isEulRo) {
                firstValue = !firstValue;
            }
            return firstValue;
        }
        return null;
    }
}
