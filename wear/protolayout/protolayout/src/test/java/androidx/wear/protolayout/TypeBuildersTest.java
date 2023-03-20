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

package androidx.wear.protolayout;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.wear.protolayout.expression.DynamicBuilders;
import androidx.wear.protolayout.proto.TypesProto;

import org.junit.Test;

public class TypeBuildersTest {
    private static final String STATE_KEY = "state-key";
    private static final TypeBuilders.StringProp STRING_PROP =
            new TypeBuilders.StringProp.Builder("string")
                    .setDynamicValue(DynamicBuilders.DynamicString.fromState(STATE_KEY))
                    .build();

    @SuppressWarnings("deprecation")
    private static final TypeBuilders.StringProp.Builder STRING_PROP_BUILDER_WITHOUT_STATIC_VALUE =
            new TypeBuilders.StringProp.Builder()
                    .setDynamicValue(DynamicBuilders.DynamicString.fromState(STATE_KEY));

    @Test
    public void stringPropSupportsDynamicString() {
        TypesProto.StringProp stringPropProto = STRING_PROP.toProto();

        assertThat(stringPropProto.getValue()).isEqualTo(STRING_PROP.getValue());
        assertThat(stringPropProto.getDynamicValue().getStateSource().getSourceKey())
                .isEqualTo(STATE_KEY);
    }

    @Test
    public void stringProp_withoutStaticValue_throws() {
        assertThrows(IllegalStateException.class, STRING_PROP_BUILDER_WITHOUT_STATIC_VALUE::build);
    }
}
