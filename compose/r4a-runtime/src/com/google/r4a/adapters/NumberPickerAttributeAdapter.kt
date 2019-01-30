@file:Suppress("unused")

package com.google.r4a.adapters

import android.widget.NumberPicker
import com.google.r4a.annotations.ConflictsWith
import com.google.r4a.annotations.RequiresOneOf

private val key = tagKey("NumberPickerInputController")

private val NumberPicker.controller: NumberPickerInputController
    get() {
        var controller = getTag(key) as? NumberPickerInputController
        if (controller == null) {
            controller = NumberPickerInputController(this)
            setTag(key, controller)
            setOnValueChangedListener(controller)
        }
        return controller
    }

@ConflictsWith("onValueChangedListener")
@RequiresOneOf("controlledValue")
fun NumberPicker.setOnValueChange(onValueChange: (Int) -> Unit) {
    controller.onValueChange = onValueChange
}

@ConflictsWith("value")
@RequiresOneOf("onValueChange")
fun NumberPicker.setControlledValue(value: Int) {
    controller.setValueIfNeeded(value)
}