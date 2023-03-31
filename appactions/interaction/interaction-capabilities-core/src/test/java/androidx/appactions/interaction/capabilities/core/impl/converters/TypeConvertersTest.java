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

package androidx.appactions.interaction.capabilities.core.impl.converters;

import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.BOOLEAN_PARAM_VALUE_CONVERTER;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.INTEGER_PARAM_VALUE_CONVERTER;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.ITEM_LIST_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.LIST_ITEM_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.ORDER_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.PARTICIPANT_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.RECIPIENT_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.SAFETY_CHECK_TYPE_SPEC;
import static androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.TIMER_TYPE_SPEC;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.appactions.interaction.capabilities.core.impl.exceptions.StructConversionException;
import androidx.appactions.interaction.capabilities.core.values.CalendarEvent;
import androidx.appactions.interaction.capabilities.core.values.Call;
import androidx.appactions.interaction.capabilities.core.values.EntityValue;
import androidx.appactions.interaction.capabilities.core.values.ItemList;
import androidx.appactions.interaction.capabilities.core.values.ListItem;
import androidx.appactions.interaction.capabilities.core.values.Message;
import androidx.appactions.interaction.capabilities.core.values.Order;
import androidx.appactions.interaction.capabilities.core.values.OrderItem;
import androidx.appactions.interaction.capabilities.core.values.Organization;
import androidx.appactions.interaction.capabilities.core.values.ParcelDelivery;
import androidx.appactions.interaction.capabilities.core.values.Person;
import androidx.appactions.interaction.capabilities.core.values.SafetyCheck;
import androidx.appactions.interaction.capabilities.core.values.SearchAction;
import androidx.appactions.interaction.capabilities.core.values.Timer;
import androidx.appactions.interaction.capabilities.core.values.properties.Participant;
import androidx.appactions.interaction.capabilities.core.values.properties.Recipient;
import androidx.appactions.interaction.proto.Entity;
import androidx.appactions.interaction.proto.ParamValue;
import androidx.appactions.interaction.protobuf.ListValue;
import androidx.appactions.interaction.protobuf.Struct;
import androidx.appactions.interaction.protobuf.Value;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public final class TypeConvertersTest {
    private static Value structToValue(Struct struct) {
        return Value.newBuilder().setStructValue(struct).build();
    }

    private static final Order ORDER_JAVA_THING =
            Order.newBuilder()
                    .setId("id")
                    .setName("name")
                    .addOrderedItem(OrderItem.newBuilder().setName("apples").build())
                    .addOrderedItem(OrderItem.newBuilder().setName("oranges").build())
                    .setSeller(Organization.newBuilder().setName("Google").build())
                    .setOrderDate(ZonedDateTime.of(2022, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC))
                    .setOrderStatus(Order.OrderStatus.ORDER_DELIVERED)
                    .setOrderDelivery(
                            ParcelDelivery.newBuilder()
                                    .setDeliveryAddress("test address")
                                    .setDeliveryMethod("UPS")
                                    .setTrackingNumber("A12345")
                                    .setTrackingUrl("https://")
                                    .build())
                    .build();
    private static final Person PERSON_JAVA_THING =
            Person.newBuilder()
                    .setName("name")
                    .setEmail("email")
                    .setTelephone("telephone")
                    .setId("id")
                    .build();
    private static final Person PERSON_JAVA_THING_2 = Person.newBuilder().setId("id2").build();
    private static final CalendarEvent CALENDAR_EVENT_JAVA_THING =
            CalendarEvent.newBuilder()
                    .setStartDate(ZonedDateTime.of(2022, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC))
                    .setEndDate(ZonedDateTime.of(2023, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC))
                    .addAttendee(PERSON_JAVA_THING)
                    .addAttendee(PERSON_JAVA_THING_2)
                    .build();
    private static final Call CALL_JAVA_THING =
            Call.newBuilder()
                    .setId("id")
                    .setCallFormat(Call.CallFormat.AUDIO)
                    .addParticipant(PERSON_JAVA_THING)
                    .build();
    private static final Message MESSAGE_JAVA_THING =
            Message.newBuilder()
                    .setId("id")
                    .addRecipient(PERSON_JAVA_THING)
                    .setMessageText("hello")
                    .build();
    private static final SafetyCheck SAFETY_CHECK_JAVA_THING =
            SafetyCheck.newBuilder()
                    .setId("id")
                    .setDuration(Duration.ofMinutes(5))
                    .setCheckinTime(ZonedDateTime.of(2023, 01, 10, 10, 0, 0, 0, ZoneOffset.UTC))
                    .build();
    private static final ListValue ORDER_ITEMS_STRUCT =
            ListValue.newBuilder()
                    .addValues(
                            Value.newBuilder()
                                    .setStructValue(
                                            Struct.newBuilder()
                                                    .putFields(
                                                            "@type",
                                                            Value.newBuilder()
                                                                    .setStringValue("OrderItem")
                                                                    .build())
                                                    .putFields(
                                                            "name",
                                                            Value.newBuilder()
                                                                    .setStringValue("apples")
                                                                    .build()))
                                    .build())
                    .addValues(
                            Value.newBuilder()
                                    .setStructValue(
                                            Struct.newBuilder()
                                                    .putFields(
                                                            "@type",
                                                            Value.newBuilder()
                                                                    .setStringValue("OrderItem")
                                                                    .build())
                                                    .putFields(
                                                            "name",
                                                            Value.newBuilder()
                                                                    .setStringValue("oranges")
                                                                    .build()))
                                    .build())
                    .build();
    private static final Struct PARCEL_DELIVERY_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("ParcelDelivery").build())
                    .putFields(
                            "deliveryAddress",
                            Value.newBuilder().setStringValue("test address").build())
                    .putFields(
                            "hasDeliveryMethod", Value.newBuilder().setStringValue("UPS").build())
                    .putFields(
                            "trackingNumber", Value.newBuilder().setStringValue("A12345").build())
                    .putFields("trackingUrl", Value.newBuilder().setStringValue("https://").build())
                    .build();
    private static final Struct ORGANIZATION_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Organization").build())
                    .putFields("name", Value.newBuilder().setStringValue("Google").build())
                    .build();
    private static final Struct ORDER_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Order").build())
                    .putFields("identifier", Value.newBuilder().setStringValue("id").build())
                    .putFields("name", Value.newBuilder().setStringValue("name").build())
                    .putFields(
                            "orderDate",
                            Value.newBuilder().setStringValue("2022-01-01T08:00Z").build())
                    .putFields(
                            "orderDelivery",
                            Value.newBuilder().setStructValue(PARCEL_DELIVERY_STRUCT).build())
                    .putFields(
                            "orderedItem",
                            Value.newBuilder().setListValue(ORDER_ITEMS_STRUCT).build())
                    .putFields(
                            "orderStatus",
                            Value.newBuilder().setStringValue("OrderDelivered").build())
                    .putFields(
                            "seller",
                            Value.newBuilder().setStructValue(ORGANIZATION_STRUCT).build())
                    .build();
    private static final Struct PERSON_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Person").build())
                    .putFields("identifier", Value.newBuilder().setStringValue("id").build())
                    .putFields("name", Value.newBuilder().setStringValue("name").build())
                    .putFields("email", Value.newBuilder().setStringValue("email").build())
                    .putFields("telephone", Value.newBuilder().setStringValue("telephone").build())
                    .build();
    private static final Struct PERSON_STRUCT_2 =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Person").build())
                    .putFields("identifier", Value.newBuilder().setStringValue("id2").build())
                    .build();
    private static final Value CALENDAR_EVENT_VALUE =
            structToValue(
                    Struct.newBuilder()
                            .putFields(
                                    "@type",
                                    Value.newBuilder().setStringValue("CalendarEvent").build())
                            .putFields(
                                    "startDate",
                                    Value.newBuilder().setStringValue("2022-01-01T08:00Z").build())
                            .putFields(
                                    "endDate",
                                    Value.newBuilder().setStringValue("2023-01-01T08:00Z").build())
                            .putFields(
                                    "attendee",
                                    Value.newBuilder()
                                            .setListValue(
                                                    ListValue.newBuilder()
                                                            .addValues(
                                                                    Value.newBuilder()
                                                                            .setStructValue(
                                                                                    PERSON_STRUCT)
                                                                            .build())
                                                            .addValues(
                                                                    Value.newBuilder()
                                                                            .setStructValue(
                                                                                    PERSON_STRUCT_2)
                                                                            .build()))
                                            .build())
                            .build());
    private static final Struct CALL_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Call").build())
                    .putFields("callFormat", Value.newBuilder().setStringValue("Audio").build())
                    .putFields(
                            "participant",
                            Value.newBuilder()
                                    .setListValue(
                                            ListValue.newBuilder()
                                                    .addValues(
                                                            Value.newBuilder()
                                                                    .setStructValue(PERSON_STRUCT)))
                                    .build())
                    .build();
    private static final Struct MESSAGE_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("Message").build())
                    .putFields(
                            "recipient",
                            Value.newBuilder()
                                    .setListValue(
                                            ListValue.newBuilder()
                                                    .addValues(
                                                            Value.newBuilder()
                                                                    .setStructValue(PERSON_STRUCT))
                                                    .build())
                                    .build())
                    .putFields("text", Value.newBuilder().setStringValue("hello").build())
                    .build();
    private static final Struct SAFETY_CHECK_STRUCT =
            Struct.newBuilder()
                    .putFields("@type", Value.newBuilder().setStringValue("SafetyCheck").build())
                    .putFields("identifier", Value.newBuilder().setStringValue("id").build())
                    .putFields("duration", Value.newBuilder().setStringValue("PT5M").build())
                    .putFields(
                            "checkinTime",
                            Value.newBuilder().setStringValue("2023-01-10T10:00Z").build())
                    .build();

    private static ParamValue toParamValue(Struct struct, String identifier) {
        return ParamValue.newBuilder().setIdentifier(identifier).setStructValue(struct).build();
    }

    private static Entity toEntity(Struct struct) {
        return Entity.newBuilder().setIdentifier("id").setStructValue(struct).build();
    }

    @Test
    public void toEntityValue() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder()
                                .setIdentifier("entity-id")
                                .setStringValue("string-val")
                                .build());

        assertThat(
                        SlotTypeConverter.ofSingular(TypeConverters.ENTITY_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo(
                        EntityValue.newBuilder().setId("entity-id").setValue("string-val").build());
    }

    @Test
    public void toIntegerValue() throws Exception {
        ParamValue paramValue = ParamValue.newBuilder().setNumberValue(5).build();
        List<ParamValue> input = Collections.singletonList(paramValue);

        assertThat(SlotTypeConverter.ofSingular(INTEGER_PARAM_VALUE_CONVERTER).convert(input))
                .isEqualTo(5);

        assertThat(INTEGER_PARAM_VALUE_CONVERTER.toParamValue(5)).isEqualTo(paramValue);
        assertThat(INTEGER_PARAM_VALUE_CONVERTER.fromParamValue(paramValue)).isEqualTo(5);
    }

    @Test
    public void toStringValue_fromList() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("hello world").build());

        assertThat(
                        SlotTypeConverter.ofSingular(TypeConverters.STRING_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo("hello world");
    }

    @Test
    public void toStringValue_withIdentifier() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder()
                                .setIdentifier("id1")
                                .setStringValue("hello world")
                                .build());

        assertThat(
                        SlotTypeConverter.ofSingular(TypeConverters.STRING_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo("id1");
    }

    @Test
    public void toStringValue_fromSingleParam() throws Exception {
        ParamValue input = ParamValue.newBuilder().setStringValue("hello world").build();

        assertThat(TypeConverters.STRING_PARAM_VALUE_CONVERTER.fromParamValue(input))
                .isEqualTo("hello world");
    }

    @Test
    public void listItem_conversions_matchesExpected() throws Exception {
        ListItem listItem = ListItem.create("itemId", "Test Item");
        Struct listItemStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("ListItem").build())
                        .putFields(
                                "identifier", Value.newBuilder().setStringValue("itemId").build())
                        .putFields("name", Value.newBuilder().setStringValue("Test Item").build())
                        .build();
        Entity listItemProto =
                Entity.newBuilder().setIdentifier("itemId").setStructValue(listItemStruct).build();

        assertThat(EntityConverter.Companion.of(LIST_ITEM_TYPE_SPEC).convert(listItem))
                .isEqualTo(listItemProto);
        assertThat(
                        ParamValueConverter.Companion.of(LIST_ITEM_TYPE_SPEC)
                                .fromParamValue(toParamValue(listItemStruct, "itemId")))
                .isEqualTo(listItem);
    }

    @Test
    public void itemList_conversions_matchesExpected() throws Exception {
        ItemList itemList =
                ItemList.newBuilder()
                        .setId("testList")
                        .setName("Test List")
                        .addListItem(
                                ListItem.create("item1", "apple"),
                                ListItem.create("item2", "banana"))
                        .build();
        Struct itemListStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("ItemList").build())
                        .putFields(
                                "identifier", Value.newBuilder().setStringValue("testList").build())
                        .putFields("name", Value.newBuilder().setStringValue("Test List").build())
                        .putFields(
                                "itemListElement",
                                Value.newBuilder()
                                        .setListValue(
                                                ListValue.newBuilder()
                                                        .addValues(
                                                                Value.newBuilder()
                                                                        .setStructValue(
                                                                                Struct.newBuilder()
                                                                                        .putFields(
                                                                                                "@type",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "ListItem")
                                                                                                        .build())
                                                                                        .putFields(
                                                                                                "identifier",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "item1")
                                                                                                        .build())
                                                                                        .putFields(
                                                                                                "name",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "apple")
                                                                                                        .build())
                                                                                        .build())
                                                                        .build())
                                                        .addValues(
                                                                Value.newBuilder()
                                                                        .setStructValue(
                                                                                Struct.newBuilder()
                                                                                        .putFields(
                                                                                                "@type",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "ListItem")
                                                                                                        .build())
                                                                                        .putFields(
                                                                                                "identifier",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "item2")
                                                                                                        .build())
                                                                                        .putFields(
                                                                                                "name",
                                                                                                Value
                                                                                                        .newBuilder()
                                                                                                        .setStringValue(
                                                                                                                "banana")
                                                                                                        .build())
                                                                                        .build())
                                                                        .build())
                                                        .build())
                                        .build())
                        .build();
        Entity itemListProto =
                Entity.newBuilder()
                        .setIdentifier("testList")
                        .setStructValue(itemListStruct)
                        .build();

        assertThat(EntityConverter.Companion.of(ITEM_LIST_TYPE_SPEC).convert(itemList))
                .isEqualTo(itemListProto);
        assertThat(
                        ParamValueConverter.Companion.of(ITEM_LIST_TYPE_SPEC)
                                .fromParamValue(toParamValue(itemListStruct, "testList")))
                .isEqualTo(itemList);
    }

    @Test
    public void order_conversions_matchesExpected() throws Exception {
        EntityConverter<Order> entityConverter = EntityConverter.Companion.of(ORDER_TYPE_SPEC);
        ParamValueConverter<Order> paramValueConverter =
                ParamValueConverter.Companion.of(ORDER_TYPE_SPEC);

        assertThat(paramValueConverter.toParamValue(ORDER_JAVA_THING))
                .isEqualTo(toParamValue(ORDER_STRUCT, "id"));
        assertThat(paramValueConverter.fromParamValue(toParamValue(ORDER_STRUCT, "id")))
                .isEqualTo(ORDER_JAVA_THING);
        assertThat(entityConverter.convert(ORDER_JAVA_THING)).isEqualTo(toEntity(ORDER_STRUCT));
    }

    @Test
    public void participant_conversions_matchesExpected() throws Exception {
        ParamValueConverter<Participant> paramValueConverter =
                ParamValueConverter.Companion.of(PARTICIPANT_TYPE_SPEC);
        ParamValue paramValue =
                ParamValue.newBuilder()
                        .setIdentifier(PERSON_JAVA_THING.getId().orElse("id"))
                        .setStructValue(PERSON_STRUCT)
                        .build();
        Participant participant = new Participant(PERSON_JAVA_THING);

        assertThat(paramValueConverter.toParamValue(participant)).isEqualTo(paramValue);
        assertThat(paramValueConverter.fromParamValue(paramValue)).isEqualTo(participant);
    }

    @Test
    public void calendarEvent_conversions_matchesExpected() throws Exception {
        assertThat(TypeConverters.CALENDAR_EVENT_TYPE_SPEC.toValue(CALENDAR_EVENT_JAVA_THING))
                .isEqualTo(CALENDAR_EVENT_VALUE);
        assertThat(TypeConverters.CALENDAR_EVENT_TYPE_SPEC.fromValue(CALENDAR_EVENT_VALUE))
                .isEqualTo(CALENDAR_EVENT_JAVA_THING);
    }

    @Test
    public void recipient_conversions_matchesExpected() throws Exception {
        ParamValueConverter<Recipient> paramValueConverter =
                ParamValueConverter.Companion.of(RECIPIENT_TYPE_SPEC);
        ParamValue paramValue =
                ParamValue.newBuilder()
                        .setIdentifier(PERSON_JAVA_THING.getId().orElse("id"))
                        .setStructValue(PERSON_STRUCT)
                        .build();
        Recipient recipient = new Recipient(PERSON_JAVA_THING);

        assertThat(paramValueConverter.toParamValue(recipient)).isEqualTo(paramValue);
        assertThat(paramValueConverter.fromParamValue(paramValue)).isEqualTo(recipient);
    }

    @Test
    public void toParticipant_unexpectedType_throwsException() {
        ParamValueConverter<Participant> paramValueConverter =
                ParamValueConverter.Companion.of(PARTICIPANT_TYPE_SPEC);
        Struct malformedStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("Malformed").build())
                        .build();

        assertThrows(
                StructConversionException.class,
                () -> paramValueConverter.fromParamValue(toParamValue(malformedStruct, "id")));
    }

    @Test
    public void toRecipient_unexpectedType_throwsException() {
        ParamValueConverter<Recipient> paramValueConverter =
                ParamValueConverter.Companion.of(RECIPIENT_TYPE_SPEC);
        Struct malformedStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("Malformed").build())
                        .build();

        assertThrows(
                StructConversionException.class,
                () -> paramValueConverter.fromParamValue(toParamValue(malformedStruct, "id")));
    }

    @Test
    public void itemList_malformedStruct_throwsException() {
        ParamValueConverter<ItemList> paramValueConverter =
                ParamValueConverter.Companion.of(ITEM_LIST_TYPE_SPEC);
        Struct malformedStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("Malformed").build())
                        .putFields("name", Value.newBuilder().setStringValue("List Name").build())
                        .putFields("identifier", Value.newBuilder().setStringValue("list1").build())
                        .build();

        assertThrows(
                StructConversionException.class,
                () -> paramValueConverter.fromParamValue(toParamValue(malformedStruct, "list1")));
    }

    @Test
    public void listItem_malformedStruct_throwsException() throws Exception {
        ParamValueConverter<ListItem> paramValueConverter =
                ParamValueConverter.Companion.of(LIST_ITEM_TYPE_SPEC);
        Struct malformedStruct =
                Struct.newBuilder()
                        .putFields("@type", Value.newBuilder().setStringValue("Malformed").build())
                        .putFields("name", Value.newBuilder().setStringValue("Apple").build())
                        .putFields("identifier", Value.newBuilder().setStringValue("item1").build())
                        .build();

        assertThrows(
                StructConversionException.class,
                () -> paramValueConverter.fromParamValue(toParamValue(malformedStruct, "item1")));
    }

    @Test
    public void toBoolean_success() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(ParamValue.newBuilder().setBoolValue(false).build());

        assertThat(SlotTypeConverter.ofSingular(BOOLEAN_PARAM_VALUE_CONVERTER).convert(input))
                .isFalse();
    }

    @Test
    public void toBoolean_throwsException() {
        List<ParamValue> input = Collections.singletonList(ParamValue.getDefaultInstance());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(BOOLEAN_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Cannot parse boolean because bool_value is missing from ParamValue.");
    }

    @Test
    public void toInteger_throwsException() {
        List<ParamValue> input = Collections.singletonList(ParamValue.getDefaultInstance());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(INTEGER_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Cannot parse integer because number_value is missing from ParamValue.");
    }

    @Test
    public void toLocalDate_success() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("2018-06-17").build());

        assertThat(
                        SlotTypeConverter.ofSingular(
                                        TypeConverters.LOCAL_DATE_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo(LocalDate.of(2018, 6, 17));
    }

    @Test
    public void toLocalDate_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("2018-0617").build());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.LOCAL_DATE_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Failed to parse ISO 8601 string to LocalDate");
    }

    @Test
    public void toLocalDateMissingValue_throwsException() {
        List<ParamValue> input = Collections.singletonList(ParamValue.getDefaultInstance());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.LOCAL_DATE_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Cannot parse date because string_value is missing from ParamValue.");
    }

    @Test
    public void toLocalTime_success() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("15:10:05").build());

        assertThat(
                        SlotTypeConverter.ofSingular(
                                        TypeConverters.LOCAL_TIME_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo(LocalTime.of(15, 10, 5));
    }

    @Test
    public void toLocalTime_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(ParamValue.newBuilder().setStringValue("15:1:5").build());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.LOCAL_TIME_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Failed to parse ISO 8601 string to LocalTime");
    }

    @Test
    public void toLocalTimeMissingValue_throwsException() {
        List<ParamValue> input = Collections.singletonList(ParamValue.getDefaultInstance());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.LOCAL_TIME_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Cannot parse time because string_value is missing from ParamValue.");
    }

    @Test
    public void toZoneId_success() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("America/New_York").build());

        assertThat(
                        SlotTypeConverter.ofSingular(TypeConverters.ZONE_ID_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    public void toZoneId_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("America/New_Yo").build());

        ZoneRulesException thrown =
                assertThrows(
                        ZoneRulesException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.ZONE_ID_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown).hasMessageThat().isEqualTo("Unknown time-zone ID: America/New_Yo");
    }

    @Test
    public void toZoneIdMissingValue_throwsException() {
        List<ParamValue> input = Collections.singletonList(ParamValue.getDefaultInstance());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.ZONE_ID_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Cannot parse ZoneId because string_value is missing from ParamValue.");
    }

    @Test
    public void toZonedDateTime_fromList() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder().setStringValue("2018-06-17T15:10:05Z").build());

        assertThat(
                        SlotTypeConverter.ofSingular(
                                        TypeConverters.ZONED_DATETIME_PARAM_VALUE_CONVERTER)
                                .convert(input))
                .isEqualTo(ZonedDateTime.of(2018, 6, 17, 15, 10, 5, 0, ZoneOffset.UTC));
    }

    @Test
    public void toZonedDateTime_invalidStringFormat_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(
                        ParamValue.newBuilder()
                                .setStringValue("Failed to parse ISO 8601 string to ZonedDateTime")
                                .build());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.ZONED_DATETIME_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo("Failed to parse ISO 8601 string to ZonedDateTime");
    }

    @Test
    public void toZonedDateTime_stringTypeMissing_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(ParamValue.newBuilder().setNumberValue(100).build());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.ZONED_DATETIME_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        "Cannot parse datetime because string_value is missing from ParamValue.");
    }

    @Test
    public void toDuration_success() throws Exception {
        List<ParamValue> input =
                Collections.singletonList(ParamValue.newBuilder().setStringValue("PT5M").build());

        Duration convertedDuration =
                SlotTypeConverter.ofSingular(TypeConverters.DURATION_PARAM_VALUE_CONVERTER)
                        .convert(input);

        assertThat(convertedDuration).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    public void toDuration_stringTypeMissing_throwsException() {
        List<ParamValue> input =
                Collections.singletonList(ParamValue.newBuilder().setNumberValue(100).build());

        StructConversionException thrown =
                assertThrows(
                        StructConversionException.class,
                        () ->
                                SlotTypeConverter.ofSingular(
                                                TypeConverters.DURATION_PARAM_VALUE_CONVERTER)
                                        .convert(input));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        "Cannot parse duration because string_value is missing from ParamValue.");
    }

    @Test
    public void searchActionConverter_withoutNestedObject() throws Exception {
        ParamValue input =
                ParamValue.newBuilder()
                        .setStructValue(
                                Struct.newBuilder()
                                        .putFields(
                                                "@type",
                                                Value.newBuilder()
                                                        .setStringValue("SearchAction")
                                                        .build())
                                        .putFields(
                                                "query",
                                                Value.newBuilder()
                                                        .setStringValue("grocery")
                                                        .build())
                                        .build())
                        .build();

        SearchAction<ItemList> output =
                TypeConverters.createSearchActionConverter(TypeConverters.ITEM_LIST_TYPE_SPEC)
                        .toSearchAction(input);

        assertThat(output).isEqualTo(SearchAction.newBuilder().setQuery("grocery").build());
    }

    @Test
    public void searchActionConverter_withNestedObject() throws Exception {
        ItemList itemList =
                ItemList.newBuilder()
                        .addListItem(ListItem.newBuilder().setName("sugar").build())
                        .build();
        Struct nestedObject = TypeConverters.ITEM_LIST_TYPE_SPEC.toValue(itemList).getStructValue();
        ParamValue input =
                ParamValue.newBuilder()
                        .setStructValue(
                                Struct.newBuilder()
                                        .putFields(
                                                "@type",
                                                Value.newBuilder()
                                                        .setStringValue("SearchAction")
                                                        .build())
                                        .putFields(
                                                "object",
                                                Value.newBuilder()
                                                        .setStructValue(nestedObject)
                                                        .build())
                                        .build())
                        .build();

        SearchAction<ItemList> output =
                TypeConverters.createSearchActionConverter(TypeConverters.ITEM_LIST_TYPE_SPEC)
                        .toSearchAction(input);

        assertThat(output).isEqualTo(SearchAction.newBuilder().setObject(itemList).build());
    }

    @Test
    public void toParamValues_string_success() {
        ParamValue output = TypeConverters.STRING_PARAM_VALUE_CONVERTER.toParamValue("grocery");

        assertThat(output).isEqualTo(ParamValue.newBuilder().setStringValue("grocery").build());
    }

    @Test
    public void toTimer_success() throws Exception {
        ParamValueConverter<Timer> paramValueConverter =
                ParamValueConverter.Companion.of(TIMER_TYPE_SPEC);
        Timer timer = Timer.newBuilder().setId("abc").build();

        assertThat(
                        paramValueConverter.fromParamValue(
                                ParamValue.newBuilder()
                                        .setStructValue(
                                                Struct.newBuilder()
                                                        .putFields(
                                                                "@type",
                                                                Value.newBuilder()
                                                                        .setStringValue("Timer")
                                                                        .build())
                                                        .putFields(
                                                                "identifier",
                                                                Value.newBuilder()
                                                                        .setStringValue("abc")
                                                                        .build()))
                                        .build()))
                .isEqualTo(timer);
    }

    @Test
    public void toParamValues_call_success() {
        assertThat(TypeConverters.toParamValue(CALL_JAVA_THING))
                .isEqualTo(
                        ParamValue.newBuilder()
                                .setStructValue(CALL_STRUCT)
                                .setIdentifier("id")
                                .build());
    }

    @Test
    public void toParamValues_message_success() {
        assertThat(TypeConverters.toParamValue(MESSAGE_JAVA_THING))
                .isEqualTo(
                        ParamValue.newBuilder()
                                .setStructValue(MESSAGE_STRUCT)
                                .setIdentifier("id")
                                .build());
    }

    @Test
    public void toParamValues_safetyCheck_success() {
        assertThat(
                        ParamValueConverter.Companion.of(SAFETY_CHECK_TYPE_SPEC)
                                .toParamValue(SAFETY_CHECK_JAVA_THING))
                .isEqualTo(
                        ParamValue.newBuilder()
                                .setStructValue(SAFETY_CHECK_STRUCT)
                                .setIdentifier("id")
                                .build());
    }
}
