/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.support.tools.jetifier.core.transform.pom

import android.support.tools.jetifier.core.utils.Log
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * Utilities for handling XML documents.
 */
class XmlUtils {

    companion object {

        private val variablePattern = Pattern.compile("\\$\\{([^}]*)}")

        /** Saves the given [Document] to a new byte array */
        fun convertDocumentToByteArray(document : Document) : ByteArray {
            val xmlOutput = XMLOutputter()
            ByteArrayOutputStream().use {
                xmlOutput.format = Format.getPrettyFormat()
                xmlOutput.output(document, it)
                return it.toByteArray()
            }
        }

        /** Creates a new [Document] from the given [ByteArray] */
        fun createDocumentFromByteArray(data: ByteArray) : Document {
            val builder = SAXBuilder()
            data.inputStream().use {
                return builder.build(it)
            }
        }

        /**
         * Creates a new XML element with the given [id] and text given in [value] and puts it under
         * the given [parent]. Nothing is created if the [value] argument is null or empty.
         */
        fun addStringNodeToNode(parent: Element, id: String, value: String?) {
            if (value.isNullOrEmpty()) {
                return
            }

            val element = Element(id)
            element.text = value
            element.namespace = parent.namespace
            parent.children.add(element)
        }


        fun resolveValue(value: String?, properties: Map<String, String>) : String? {
            if (value == null) {
                return null
            }

            val matcher = variablePattern.matcher(value)
            if (matcher.matches()) {
                val variableName = matcher.group(1)
                val varValue = properties[variableName]
                if (varValue == null) {
                    Log.e("TAG", "Failed to resolve variable '%s'", value)
                    return value
                }
                return varValue
            }

            return value
        }
    }

}