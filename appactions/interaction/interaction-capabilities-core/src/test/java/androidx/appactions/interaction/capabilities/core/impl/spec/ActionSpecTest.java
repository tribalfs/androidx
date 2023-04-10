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

package androidx.appactions.interaction.capabilities.core.impl.spec;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf;
import androidx.appactions.interaction.capabilities.core.impl.converters.EntityConverter;
import androidx.appactions.interaction.capabilities.core.impl.converters.ParamValueConverter;
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters;
import androidx.appactions.interaction.capabilities.core.properties.Entity;
import androidx.appactions.interaction.capabilities.core.properties.Property;
import androidx.appactions.interaction.capabilities.core.properties.StringValue;
import androidx.appactions.interaction.capabilities.core.testing.spec.Output;
import androidx.appactions.interaction.capabilities.core.values.EntityValue;
import androidx.appactions.interaction.proto.AppActionsContext.AppAction;
import androidx.appactions.interaction.proto.AppActionsContext.IntentParameter;
import androidx.appactions.interaction.proto.FulfillmentResponse.StructuredOutput;
import androidx.appactions.interaction.proto.FulfillmentResponse.StructuredOutput.OutputValue;
import androidx.appactions.interaction.proto.ParamValue;

import com.google.auto.value.AutoValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Optional;

@RunWith(JUnit4.class)
public final class ActionSpecTest {

    private static final ActionSpec<Properties, Arguments, Output> ACTION_SPEC =
            ActionSpecBuilder.ofCapabilityNamed("actions.intent.TEST")
                    .setDescriptor(Properties.class)
                    .setArguments(Arguments.class, Arguments::newBuilder)
                    .setOutput(Output.class)
                    .bindParameter(
                            "requiredEntity",
                            Properties::requiredEntityField,
                            Arguments.Builder::setRequiredEntityField,
                            TypeConverters.ENTITY_PARAM_VALUE_CONVERTER,
                            TypeConverters.ENTITY_ENTITY_CONVERTER)
                    .bindOptionalParameter(
                            "optionalEntity",
                            Properties::optionalEntityField,
                            Arguments.Builder::setOptionalEntityField,
                            TypeConverters.ENTITY_PARAM_VALUE_CONVERTER,
                            TypeConverters.ENTITY_ENTITY_CONVERTER)
                    .bindRepeatedParameter(
                            "repeatedEntity",
                            Properties::repeatedEntityField,
                            Arguments.Builder::setRepeatedEntityField,
                            TypeConverters.ENTITY_PARAM_VALUE_CONVERTER,
                            TypeConverters.ENTITY_ENTITY_CONVERTER)
                    .bindParameter(
                            "requiredString",
                            Properties::requiredStringField,
                            Arguments.Builder::setRequiredStringField,
                            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
                            TypeConverters.STRING_VALUE_ENTITY_CONVERTER)
                    .bindOptionalParameter(
                            "optionalString",
                            Properties::optionalStringField,
                            Arguments.Builder::setOptionalStringField,
                            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
                            TypeConverters.STRING_VALUE_ENTITY_CONVERTER)
                    .bindRepeatedParameter(
                            "repeatedString",
                            Properties::repeatedStringField,
                            Arguments.Builder::setRepeatedStringField,
                            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
                            TypeConverters.STRING_VALUE_ENTITY_CONVERTER)
                    .bindOptionalOutput(
                            "optionalStringOutput",
                            Output::optionalStringField,
                            TypeConverters.STRING_PARAM_VALUE_CONVERTER::toParamValue)
                    .bindRepeatedOutput(
                            "repeatedStringOutput",
                            Output::repeatedStringField,
                            TypeConverters.STRING_PARAM_VALUE_CONVERTER::toParamValue)
                    .build();
    private static final ParamValueConverter<String> STRING_PARAM_VALUE_CONVERTER =
            new ParamValueConverter<String>() {
                @NonNull
                @Override
                public ParamValue toParamValue(String type) {
                    return ParamValue.newBuilder().setStringValue(type).build();
                }

                @Override
                public String fromParamValue(@NonNull ParamValue paramValue) {
                    return "test";
                }
            };
    private static final EntityConverter<String> STRING_ENTITY_CONVERTER =
            (theString) ->
                    androidx.appactions.interaction.proto.Entity.newBuilder()
                            .setIdentifier(theString)
                            .setName(theString)
                            .build();

    private static final ActionSpec<GenericEntityProperty, GenericEntityArguments, Output>
            GENERIC_TYPES_ACTION_SPEC =
                    ActionSpecBuilder.ofCapabilityNamed("actions.intent.TEST")
                            .setDescriptor(GenericEntityProperty.class)
                            .setArguments(GenericEntityArguments.class,
                                    GenericEntityArguments::newBuilder)
                            .setOutput(Output.class)
                            .bindParameter(
                                    "requiredEntity",
                                    GenericEntityProperty::singularField,
                                    GenericEntityArguments.Builder::setSingularField,
                                    STRING_PARAM_VALUE_CONVERTER,
                                    STRING_ENTITY_CONVERTER)
                            .bindOptionalParameter("optionalEntity",
                                    GenericEntityProperty::optionalField,
                                    GenericEntityArguments.Builder::setOptionalField,
                                    STRING_PARAM_VALUE_CONVERTER,
                                    STRING_ENTITY_CONVERTER)
                            .bindRepeatedParameter("repeatedEntities",
                                    GenericEntityProperty::repeatedField,
                                    GenericEntityArguments.Builder::setRepeatedField,
                                    STRING_PARAM_VALUE_CONVERTER,
                                    STRING_ENTITY_CONVERTER)
                            .build();

    @Test
    public void getAppAction_genericParameters() {
        GenericEntityProperty property =
                GenericEntityProperty.create(
                        new Property.Builder<String>()
                                .setRequired(true)
                                .setPossibleValues("one")
                                .build(),
                        Optional.of(
                                new Property.Builder<String>()
                                        .setRequired(true)
                                        .setPossibleValues("two")
                                        .build()),
                        Optional.of(
                                new Property.Builder<String>()
                                        .setRequired(true)
                                        .setPossibleValues("three")
                                        .build()));

        assertThat(GENERIC_TYPES_ACTION_SPEC.convertPropertyToProto(property))
                .isEqualTo(
                        AppAction.newBuilder()
                                .setName("actions.intent.TEST")
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("requiredEntity")
                                                .setIsRequired(true)
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("one")
                                                                .setName("one")))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("optionalEntity")
                                                .setIsRequired(true)
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("two")
                                                                .setName("two")))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("repeatedEntities")
                                                .setIsRequired(true)
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("three")
                                                                .setName("three")))
                                .build());
    }

    @Test
    public void getAppAction_onlyRequiredProperty() {
        Properties property =
                Properties.create(
                        new Property.Builder<Entity>()
                                .setPossibleValues(
                                        new Entity.Builder()
                                                .setId("contact_2")
                                                .setName("Donald")
                                                .setAlternateNames("Duck")
                                                .build())
                                .setValueMatchRequired(true)
                                .build(),
                        new Property.Builder<StringValue>().build());

        assertThat(ACTION_SPEC.convertPropertyToProto(property))
                .isEqualTo(
                        AppAction.newBuilder()
                                .setName("actions.intent.TEST")
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("requiredEntity")
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("contact_2")
                                                                .setName("Donald")
                                                                .addAlternateNames("Duck")
                                                                .build())
                                                .setIsRequired(false)
                                                .setEntityMatchRequired(true))
                                .addParams(IntentParameter.newBuilder().setName("requiredString"))
                                .build());
    }

    @Test
    public void getAppAction_allProperties() {
        Properties property =
                Properties.create(
                        new Property.Builder<Entity>()
                                .setPossibleValues(
                                        new Entity.Builder()
                                                .setId("contact_2")
                                                .setName("Donald")
                                                .setAlternateNames("Duck")
                                                .build())
                                .build(),
                        Optional.of(
                                new Property.Builder<Entity>()
                                        .setPossibleValues(
                                                new Entity.Builder()
                                                        .setId("entity1")
                                                        .setName("optional possible entity")
                                                        .build())
                                        .setRequired(true)
                                        .build()),
                        Optional.of(
                                new Property.Builder<TestEnum>()
                                        .setPossibleValues(TestEnum.VALUE_1)
                                        .setRequired(true)
                                        .build()),
                        Optional.of(
                                new Property.Builder<Entity>()
                                        .setPossibleValues(
                                                new Entity.Builder()
                                                        .setId("entity1")
                                                        .setName("repeated entity1")
                                                        .build(),
                                                new Entity.Builder()
                                                        .setId("entity2")
                                                        .setName("repeated entity2")
                                                        .build())
                                        .setRequired(true)
                                        .build()),
                        new Property.Builder<StringValue>().build(),
                        Optional.of(
                                new Property.Builder<StringValue>()
                                        .setPossibleValues(StringValue.of("value1"))
                                        .setValueMatchRequired(true)
                                        .setRequired(true)
                                        .build()),
                        Optional.of(Property.prohibited()));

        assertThat(ACTION_SPEC.convertPropertyToProto(property))
                .isEqualTo(
                        AppAction.newBuilder()
                                .setName("actions.intent.TEST")
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("requiredEntity")
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("contact_2")
                                                                .setName("Donald")
                                                                .addAlternateNames("Duck")
                                                                .build())
                                                .setIsRequired(false)
                                                .setEntityMatchRequired(false))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("optionalEntity")
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("entity1")
                                                                .setName("optional possible entity")
                                                                .build())
                                                .setIsRequired(true)
                                                .setEntityMatchRequired(false))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("repeatedEntity")
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("entity1")
                                                                .setName("repeated entity1")
                                                                .build())
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("entity2")
                                                                .setName("repeated entity2")
                                                                .build())
                                                .setIsRequired(true)
                                                .setEntityMatchRequired(false))
                                .addParams(IntentParameter.newBuilder().setName("requiredString"))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("optionalString")
                                                .addPossibleEntities(
                                                        androidx.appactions.interaction.proto.Entity
                                                                .newBuilder()
                                                                .setIdentifier("value1")
                                                                .setName("value1")
                                                                .build())
                                                .setIsRequired(true)
                                                .setEntityMatchRequired(true))
                                .addParams(
                                        IntentParameter.newBuilder()
                                                .setName("repeatedString")
                                                .setIsProhibited(true))
                                .build());
    }

    @Test
    public void convertOutputToProto_string() {
        Output output =
                Output.builder()
                        .setOptionalStringField("test2")
                        .setRepeatedStringField(List.of("test3", "test4"))
                        .build();

        StructuredOutput expectedExecutionOutput =
                StructuredOutput.newBuilder()
                        .addOutputValues(
                                OutputValue.newBuilder()
                                        .setName("optionalStringOutput")
                                        .addValues(
                                                ParamValue.newBuilder()
                                                        .setStringValue("test2")
                                                        .build())
                                        .build())
                        .addOutputValues(
                                OutputValue.newBuilder()
                                        .setName("repeatedStringOutput")
                                        .addValues(
                                                ParamValue.newBuilder()
                                                        .setStringValue("test3")
                                                        .build())
                                        .addValues(
                                                ParamValue.newBuilder()
                                                        .setStringValue("test4")
                                                        .build())
                                        .build())
                        .build();

        StructuredOutput executionOutput = ACTION_SPEC.convertOutputToProto(output);

        assertThat(executionOutput.getOutputValuesList())
                .containsExactlyElementsIn(expectedExecutionOutput.getOutputValuesList());
    }

    @Test
    public void convertOutputToProto_emptyOutput() {
        Output output = Output.builder().setRepeatedStringField(List.of("test3", "test4")).build();
        // No optionalStringOutput since it is not in the output above.
        StructuredOutput expectedExecutionOutput =
                StructuredOutput.newBuilder()
                        .addOutputValues(
                                OutputValue.newBuilder()
                                        .setName("repeatedStringOutput")
                                        .addValues(
                                                ParamValue.newBuilder()
                                                        .setStringValue("test3")
                                                        .build())
                                        .addValues(
                                                ParamValue.newBuilder()
                                                        .setStringValue("test4")
                                                        .build())
                                        .build())
                        .build();

        StructuredOutput executionOutput = ACTION_SPEC.convertOutputToProto(output);

        assertThat(executionOutput.getOutputValuesList())
                .containsExactlyElementsIn(expectedExecutionOutput.getOutputValuesList());
    }

    enum TestEnum {
        VALUE_1,
        VALUE_2,
    }

    @AutoValue
    abstract static class Arguments {

        static Builder newBuilder() {
            return new AutoValue_ActionSpecTest_Arguments.Builder();
        }

        abstract EntityValue requiredEntityField();

        abstract EntityValue optionalEntityField();

        abstract List<EntityValue> repeatedEntityField();

        abstract String requiredStringField();

        abstract String optionalStringField();

        abstract List<String> repeatedStringField();

        @AutoValue.Builder
        abstract static class Builder implements BuilderOf<Arguments> {

            abstract Builder setRequiredEntityField(EntityValue value);

            abstract Builder setOptionalEntityField(EntityValue value);

            abstract Builder setRepeatedEntityField(List<EntityValue> repeated);

            abstract Builder setRequiredStringField(String value);

            abstract Builder setOptionalStringField(String value);

            abstract Builder setRepeatedStringField(List<String> repeated);

            @NonNull
            @Override
            public abstract Arguments build();
        }
    }

    @AutoValue
    abstract static class Properties {

        static Properties create(
                Property<Entity> requiredEntityField,
                Optional<Property<Entity>> optionalEntityField,
                Optional<Property<TestEnum>> optionalEnumField,
                Optional<Property<Entity>> repeatedEntityField,
                Property<StringValue> requiredStringField,
                Optional<Property<StringValue>> optionalStringField,
                Optional<Property<StringValue>> repeatedStringField) {
            return new AutoValue_ActionSpecTest_Properties(
                    requiredEntityField,
                    optionalEntityField,
                    optionalEnumField,
                    repeatedEntityField,
                    requiredStringField,
                    optionalStringField,
                    repeatedStringField);
        }

        static Properties create(
                Property<Entity> requiredEntityField,
                Property<StringValue> requiredStringField) {
            return create(
                    requiredEntityField,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    requiredStringField,
                    Optional.empty(),
                    Optional.empty());
        }

        abstract Property<Entity> requiredEntityField();

        abstract Optional<Property<Entity>> optionalEntityField();

        abstract Optional<Property<TestEnum>> optionalEnumField();

        abstract Optional<Property<Entity>> repeatedEntityField();

        abstract Property<StringValue> requiredStringField();

        abstract Optional<Property<StringValue>> optionalStringField();

        abstract Optional<Property<StringValue>> repeatedStringField();
    }

    @AutoValue
    abstract static class GenericEntityArguments {

        static Builder newBuilder() {
            return new AutoValue_ActionSpecTest_GenericEntityArguments.Builder();
        }

        abstract String singularField();

        abstract String optionalField();

        abstract List<String> repeatedField();

        @AutoValue.Builder
        abstract static class Builder implements BuilderOf<GenericEntityArguments> {

            abstract Builder setSingularField(String value);

            abstract Builder setOptionalField(String value);

            abstract Builder setRepeatedField(List<String> value);

            @NonNull
            @Override
            public abstract GenericEntityArguments build();
        }
    }

    @AutoValue
    abstract static class GenericEntityProperty {

        static GenericEntityProperty create(
                Property<String> singularField,
                Optional<Property<String>> optionalField,
                Optional<Property<String>> repeatedField) {
            return new AutoValue_ActionSpecTest_GenericEntityProperty(
                    singularField, optionalField, repeatedField);
        }

        abstract Property<String> singularField();

        abstract Optional<Property<String>> optionalField();

        abstract Optional<Property<String>> repeatedField();
    }
}
