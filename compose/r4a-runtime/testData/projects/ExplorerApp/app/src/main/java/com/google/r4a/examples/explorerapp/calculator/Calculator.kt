package com.google.r4a.examples.explorerapp.calculator

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.r4a.*
import com.google.r4a.adapters.*
import com.google.r4a.examples.explorerapp.R

class Calculator : Component() {

    var formula = CalculatorFormula()

    private val FILL = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private val WRAP_HORIZ = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private val WRAP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private val onClick = object : Function1<String, Unit>  {
        override fun invoke(p1: String) {
            formula.append(p1)
            recompose()
        }
    }

    private fun fontSize(display: String): Int {
        return when {
            display.length <= 8 -> 32
            display.length <= 11 -> 24
            else -> 12
        }
    }

    override fun compose() {

        <LinearLayout orientation="vertical" layoutParams={FILL}>
            <LinearLayout
                orientation="vertical"
                layoutParams={WRAP_HORIZ}
                elevation={4.dip}
                backgroundColor={Color.WHITE}
            >
                <Toolbar popupTheme={R.style.ThemeOverlay_Popup} layoutParams={WRAP_HORIZ}>
                    <TextView
                        layoutParams={WRAP}
                        allCaps={true}
                        textSize={6.sp}
                        typeface={Typeface.create("sans-serif-medium", Typeface.NORMAL)}
                        text="RAD"
                        contentDescription="radian mode"
                    />
                </Toolbar>

                <TextView
                    layoutParams={FILL}
                    ellipsize={TextUtils.TruncateAt.END}
                    maxLines={1}
                    textColor={Color.BLACK}
                    textSize={fontSize(formula.formulaString).dip}
                    height={90.dip}
                    typeface={Typeface.create("sans-serif-light", Typeface.NORMAL)}
                    text={formula.formulaString}
                    bufferType={TextView.BufferType.EDITABLE}
                    cursorVisible={true}
                    gravity={Gravity.BOTTOM or Gravity.RIGHT}
                    focusableInTouchMode={true}
                    paddingHorizontal={3.dip}
                />
                <TextView
                    layoutParams={FILL}
                    singleLine={true}
                    text={formula.previewString}
                    bufferType={TextView.BufferType.SPANNABLE}
                    typeface={Typeface.create("sans-serif-light", Typeface.NORMAL)}
                    textSize={7.dip}
                    paddingHorizontal={10.dip}
                    paddingVertical={6.dip}
                    gravity={Gravity.BOTTOM or Gravity.RIGHT}
                    cursorVisible={true}
                />
            </LinearLayout>
            <LinearLayout layoutParams={FILL}>
                <GridLayout
                    layoutParams={LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 7f)}
                    rowCount={4}
                    columnCount={3}
                    backgroundColor="#434343"
                >
                    listOf("7", "8", "9", "4", "5", "6", "1", "2", "3", ".", "0", "=").forEachIndexed { index, value ->
                        <CalculatorButton value onClick layoutParams={grid(index / 3, index % 3)} />
                    }
                </GridLayout>
                <GridLayout
                    rowCount={5}
                    columnCount={1}
                    backgroundColor="#636363"
                    layoutParams={LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f)}
                >
                    listOf(CalculatorFormula.DELETE, CalculatorFormula.DIVIDE, CalculatorFormula.MULTIPLY,
                            CalculatorFormula.SUBTRACT, CalculatorFormula.ADD).forEachIndexed { index, value ->
                        <CalculatorButton value onClick layoutParams={grid(index, 0)} />
                    }
                </GridLayout>
            </LinearLayout>
        </LinearLayout>
    }

    /** Helper function to make it easier to create GridLayoutParams for the calculator buttons **/
    fun grid(row: Int, column: Int) = GridLayout.LayoutParams(
            GridLayout.spec(row, GridLayout.CENTER, 1f),
            GridLayout.spec(column, GridLayout.CENTER, 0f)
    )
}


private class CalculatorButton : Component() {

    lateinit var value: String
    lateinit var layoutParams: GridLayout.LayoutParams
    lateinit var onClick: (op: String) -> Unit

    private val myOnClick = object: View.OnClickListener {
        override fun onClick(v: View?) {
            onClick(value)
        }
    }

    override fun compose() {
        // TODO(lmr): for some reason this won't type check correctly if we put it in the attribute...
//        val textSize = if(CalculatorFormula.isOperator(value) || CalculatorFormula.DELETE.equals(value)) "7dip" else "7dip"
        <Button
            text={value}
            layoutParams={layoutParams}
            allCaps={true}
            gravity={Gravity.CENTER}
            includeFontPadding={false}
            typeface={Typeface.create("sans-serif-light", Typeface.NORMAL)}
            textSize={8.sp}
            textColor={Color.WHITE}
            backgroundResource={R.drawable.pad_button_background}
            onClickListener={myOnClick}
        />
    }
}
