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
package androidx.window.embedding

import android.app.Activity

/**
 * A container that holds a stack of activities, overlapping and bound to the same rectangle on the
 * screen.
 *
 * @param activities the [Activity] list in this application's process that belongs to this
 * [ActivityStack]. Activities running in other processes will not be contained in this list.
 * @param isEmpty whether there is no [Activity] running in this [ActivityStack]. It can be
 * non-empty when the [activities] is empty, because there can be [Activity] running in other
 * processes, which will not be reported in [activities].
 */
class ActivityStack(
    /**
     * The [Activity] list in this application's process that belongs to this [ActivityStack].
     *
     * Note that Activities that are running in other processes will not be contained in this
     * list. They can be in any position in terms of ordering relative to the activities in the
     * list.
     */
    internal val activities: List<Activity>,
    /**
     * Whether there is no [Activity] running in this [ActivityStack].
     *
     * Note that [activities] only report [Activity] in the process used to create this
     * ActivityStack. That said, if this ActivityStack only contains activities from other
     * process(es), [activities] will return an empty list, but this method will return `false`.
     */
    private val isEmpty: Boolean = false
) {

    operator fun contains(activity: Activity): Boolean {
        return activities.contains(activity)
    }

    /**
     * Returns `true` if there's no [Activity] running in this [ActivityStack].
     *
     * Note that [activities] only report Activity in the process used to create this
     * ActivityStack. That said, if this [ActivityStack] only contains activities from other
     * process(es), [activities] will return an empty list, but this method will return `false`.
     */
    fun isEmpty(): Boolean {
        return isEmpty
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ActivityStack

        if (activities != other.activities) return false
        if (isEmpty != other.isEmpty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = activities.hashCode()
        result = 31 * result + isEmpty.hashCode()
        return result
    }

    override fun toString(): String =
        "ActivityStack{" +
            "activities=$activities" +
            ", isEmpty=$isEmpty" +
            "}"
}
