package androidx.compose.foundation.text.modifiers

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TextAnnotatedStringNodeTest {
    @get:Rule
    val rule = createComposeRule()
    val context: Context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun draw_whenNotAttached_doesNotCrash() {
        val subject = TextAnnotatedStringNode(
            AnnotatedString("text"), TextStyle.Default, createFontFamilyResolver(context)
        )
        rule.setContent {
            Canvas(Modifier.fillMaxSize()) {
                val contentDrawScope = object : ContentDrawScope, DrawScope by this {
                    override fun drawContent() {
                        TODO("Not needed")
                    }
                } as ContentDrawScope
                with(subject) {
                    contentDrawScope.draw()
                }
            }
        }
        rule.waitForIdle()
    }
}
