/*
 * Copyright 2018 The Android Open Source Project
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

package com.example.androidx.viewpager2

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.SpinnerAdapter
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL
import com.example.androidx.viewpager2.cards.Card

/**
 * Base class for the two activities in the demo. Sets up the list of cards and implements UI to
 * jump to arbitrary cards using setCurrentItem, either with or without smooth scrolling.
 */
abstract class BaseCardActivity : FragmentActivity() {

    lateinit var viewPager: ViewPager2
    private lateinit var cardSelector: Spinner
    private lateinit var smoothScrollCheckBox: CheckBox
    private lateinit var rotateCheckBox: CheckBox
    private lateinit var translateCheckBox: CheckBox
    private lateinit var scaleCheckBox: CheckBox
    private lateinit var gotoPage: Button
    private lateinit var orientationSelector: Spinner
    private lateinit var disableUserInputCheckBox: CheckBox
    private var orientation: Int = ORIENTATION_HORIZONTAL

    private val translateX get() = orientation == ORIENTATION_VERTICAL &&
            translateCheckBox.isChecked
    private val translateY get() = orientation == ORIENTATION_HORIZONTAL &&
            translateCheckBox.isChecked

    protected open val layoutId: Int = R.layout.activity_no_tablayout

    private val mAnimator = ViewPager2.PageTransformer { page, position ->
        val absPos = Math.abs(position)
        page.apply {
            rotation = if (rotateCheckBox.isChecked) position * 360 else 0f
            translationY = if (translateY) absPos * 500f else 0f
            translationX = if (translateX) absPos * 350f else 0f
            if (scaleCheckBox.isChecked) {
                val scale = if (absPos > 1) 0F else 1 - absPos
                scaleX = scale
                scaleY = scale
            } else {
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutId)

        viewPager = findViewById(R.id.view_pager)
        orientationSelector = findViewById(R.id.orientation_spinner)
        disableUserInputCheckBox = findViewById(R.id.disable_user_input_checkbox)
        cardSelector = findViewById(R.id.card_spinner)
        smoothScrollCheckBox = findViewById(R.id.smooth_scroll_checkbox)
        rotateCheckBox = findViewById(R.id.rotate_checkbox)
        translateCheckBox = findViewById(R.id.translate_checkbox)
        scaleCheckBox = findViewById(R.id.scale_checkbox)
        gotoPage = findViewById(R.id.jump_button)

        disableUserInputCheckBox.setOnCheckedChangeListener { _, isDisabled ->
            viewPager.isUserInputEnabled = !isDisabled
        }

        orientationSelector.adapter = createOrientationAdapter()
        cardSelector.adapter = createCardAdapter()

        viewPager.setPageTransformer(mAnimator)

        orientationSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                when (parent.selectedItem.toString()) {
                    HORIZONTAL -> orientation = ORIENTATION_HORIZONTAL
                    VERTICAL -> orientation = ORIENTATION_VERTICAL
                }
                viewPager.orientation = orientation
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        gotoPage.setOnClickListener {
            val card = cardSelector.selectedItemPosition
            val smoothScroll = smoothScrollCheckBox.isChecked
            viewPager.setCurrentItem(card, smoothScroll)
        }
    }

    private fun createCardAdapter(): SpinnerAdapter {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Card.DECK)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun createOrientationAdapter(): SpinnerAdapter {
        val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, arrayOf(HORIZONTAL, VERTICAL))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    companion object {
        val cards = Card.DECK
        private const val HORIZONTAL = "horizontal"
        private const val VERTICAL = "vertical"
    }
}
