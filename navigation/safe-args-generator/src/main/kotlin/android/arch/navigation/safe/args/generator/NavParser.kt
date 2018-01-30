/*
 * Copyright 2017 The Android Open Source Project
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

package android.arch.navigation.safe.args.generator

import android.arch.navigation.safe.args.generator.models.Action
import android.arch.navigation.safe.args.generator.models.Argument
import android.arch.navigation.safe.args.generator.models.Destination
import android.arch.navigation.safe.args.generator.models.Id
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileReader

private const val TAG_NAVIGATION = "navigation"
private const val TAG_ACTION = "action"
private const val TAG_ARGUMENT = "argument"

private const val ATTRIBUTE_ID = "id"
private const val ATTRIBUTE_DESTINATION = "destination"
private const val ATTRIBUTE_DEFAULT_VALUE = "defaultValue"
private const val ATTRIBUTE_NAME = "name"
private const val ATTRIBUTE_TYPE = "type"

private const val NAMESPACE_RES_AUTO = "http://schemas.android.com/apk/res-auto"
private const val NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android"

private fun parseDestination(parser: XmlPullParser, rFilePackage: String,
                             applicationId: String): Destination {
    val type = parser.name
    val name = parser.attrValue(NAMESPACE_ANDROID, ATTRIBUTE_NAME) ?: ""
    val idValue = parser.attrValue(NAMESPACE_ANDROID, ATTRIBUTE_ID)
    val args = mutableListOf<Argument>()
    val actions = mutableListOf<Action>()
    val nested = mutableListOf<Destination>()
    parser.traverseInnerStartTags {
        when {
            parser.name == TAG_ACTION -> actions.add(parseAction(parser, rFilePackage))
            parser.name == TAG_ARGUMENT -> args.add(parseArgument(parser))
            type == TAG_NAVIGATION -> nested.add(parseDestination(parser, rFilePackage,
                    applicationId))
        }
    }

    val id = parseNullableId(idValue, rFilePackage)
    val className = Destination.createName(id, name, applicationId)
    if (className == null && (actions.isNotEmpty() || args.isNotEmpty())) {
        throw IllegalArgumentException("Destination with arguments or action mush have " +
                "either name either id attributes")
    }
    return Destination(id, className, type, args, actions, nested)
}

private fun parseArgument(parser: XmlPullParser): Argument {
    val name = parser.attrValueOrThrow(NAMESPACE_ANDROID, ATTRIBUTE_NAME)
    val defaultValue = parser.attrValue(NAMESPACE_ANDROID, ATTRIBUTE_DEFAULT_VALUE)
    val typeString = parser.attrValue(NAMESPACE_RES_AUTO, ATTRIBUTE_TYPE)
    val type = typeString?.let { NavType.parse(typeString) } ?: StringType
    defaultValue?.let { type.verify(defaultValue) }
    return Argument(name, type, defaultValue)
}

private fun parseAction(parser: XmlPullParser, rFilePackage: String): Action {
    val idValue = parser.attrValueOrThrow(NAMESPACE_ANDROID, ATTRIBUTE_ID)
    val destValue = parser.attrValue(NAMESPACE_RES_AUTO, ATTRIBUTE_DESTINATION)
    val args = mutableListOf<Argument>()
    parser.traverseInnerStartTags {
        if (parser.name == TAG_ARGUMENT) {
            args.add(parseArgument(parser))
        }
    }
    return Action(parseId(idValue, rFilePackage),
            parseNullableId(destValue, rFilePackage), args)
}

fun parseNavigationFile(navigationXml: File, rFilePackage: String,
                        applicationId: String): Destination {
    FileReader(navigationXml).use { reader ->
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(reader)
        }
        parser.traverseStartTags { true }
        return parseDestination(parser, rFilePackage, applicationId)
    }
}

// @[+][package:]id/resource_name -> package.R.id.resource_name
internal fun parseId(xmlId: String, rFilePackage: String): Id {
    val split = xmlId.split("/")
    if (split.size != 2) {
        throw IllegalArgumentException("id should be in format: " +
                "@[+][package:]id/resource_name, but is: $xmlId")
    }
    val resourceName = split[1]

    val packageName = split[0].removePrefix("@").removePrefix("+")
            .removeSuffix("id").removeSuffix(":")

    return Id(if (packageName.isNotEmpty()) packageName else rFilePackage, resourceName)
}

internal fun parseNullableId(xmlId: String?, rFilePackage: String): Id? = xmlId?.let {
    parseId(it, rFilePackage)
}