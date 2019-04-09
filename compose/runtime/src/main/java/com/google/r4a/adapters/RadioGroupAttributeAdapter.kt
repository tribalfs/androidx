@file:Suppress("unused")

package com.google.r4a.adapters

import android.widget.RadioGroup
import com.google.r4a.annotations.ConflictsWith
import com.google.r4a.annotations.RequiresOneOf

private val key = tagKey("RadioGroupInputController")

private val RadioGroup.controller: RadioGroupInputController
    get() {
        var controller = getTag(key) as? RadioGroupInputController
        if (controller == null) {
            controller = RadioGroupInputController(this)
            setTag(key, controller)
            setOnCheckedChangeListener(controller)
        }
        return controller
    }

@RequiresOneOf("controlledCheckedId")
@ConflictsWith("onCheckedChangeListener")
fun RadioGroup.setOnCheckedIdChange(onCheckedIdChange: Function1<Int, Unit>) {
    controller.onCheckedIdChange = onCheckedIdChange
}

@RequiresOneOf("onCheckedIdChange")
fun RadioGroup.setControlledCheckedId(checkedId: Int) {
    controller.setValueIfNeeded(checkedId)
}
