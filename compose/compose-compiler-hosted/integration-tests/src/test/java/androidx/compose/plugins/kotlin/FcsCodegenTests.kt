/*
 * Copyright 2019 The Android Open Source Project
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

@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.compose.plugins.kotlin

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.Composer
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URLClassLoader

private val noParameters = { emptyMap<String, String>() }

@RunWith(ComposeRobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    minSdk = 23,
    maxSdk = 23
)
class FcsCodegenTests : AbstractCodegenTest() {

    @Test
    fun testReturnValue(): Unit = ensureSetup {
        compose("""
            var a = 0
            var b = 0

            @Composable
            fun SimpleComposable() {
                a++
                val c = state { 0 }
                val d = remember(c.value) { b++; b }
                val recompose = invalidate
                Button(
                  text=listOf(a, b, c.value, d).joinToString(", "),
                  onClick={ c.value += 1 },
                  id=42
                )
                Button(
                  text="Recompose",
                  onClick={ recompose() },
                  id=43
                )
            }
        """,
            noParameters,
            "SimpleComposable()"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals(
                button.text,
                listOf(
                    1, // SimpleComposable has run once
                    1, // memo has been called once because of initial mount
                    0, // state was in itialized at 0
                    1 // memo should return b
                ).joinToString(", ")
            )
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            val recompose = activity.findViewById(43) as Button
            assertEquals(
                button.text,
                listOf(
                    2, // SimpleComposable has run twice
                    2, // memo has been called twice, because state input has changed
                    1, // state was changed to 1
                    2 // memo should return b
                ).joinToString(", ")
            )
            recompose.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals(
                button.text,
                listOf(
                    3, // SimpleComposable has run three times
                    2, // memo was not called this time, because input didn't change
                    1, // state stayed at 1
                    2 // memo should return b
                ).joinToString(", ")
            )
        }
    }

    @Test
    fun testReorderedArgsReturnValue(): Unit = ensureSetup {
        compose(
            """
            @Composable
            fun SimpleComposable() {
                val x = remember(calculation = { "abc" }, v1 = "def")
                TextView(
                  text=x,
                  id=42
                )
            }
        """,
            noParameters,
            "SimpleComposable()"
        ).then { activity ->
            val button = activity.findViewById(42) as TextView
            assertEquals(button.text, "abc")
        }
    }

    @Test
    fun testTrivialReturnValue(): Unit = ensureSetup {
        compose("""
        @Composable
        fun <T> identity(value: T): T = value

        @Composable
        fun SimpleComposable() {
            val x = identity("def")
            TextView(
              text=x,
              id=42
            )
        }
    """,
            noParameters,
            "SimpleComposable()"
        ).then { activity ->
            val button = activity.findViewById(42) as TextView
            assertEquals(button.text, "def")
        }
    }

    @Test
    fun testForDevelopment(): Unit = ensureSetup {
        codegen(
            """
            import androidx.compose.*

            @Composable
            fun bar() {

            }

            @Composable
            fun foo() {
                TextView(text="Hello World")
            }
            """
        )
    }

    @Test
    fun testSimpleFunctionResolution(): Unit = ensureSetup {
        compose(
            """
            import androidx.compose.*

            @Composable
            fun noise(text: String) {}

            @Composable
            fun bar() {
                noise(text="Hello World")
            }
            """, noParameters,
            """
            """
        )
    }

    @Test
    fun testSimpleClassResolution(): Unit = ensureSetup {
        compose(
            """
            import android.widget.TextView
            import androidx.compose.*

            @Composable
            fun bar() {
                TextView(text="Hello World")
            }
            """, noParameters,
            """
            """
        )
    }

    @Test
    fun testModelOne(): Unit = ensureSetup {
        codegen(
            """
                @Model
                class ModelClass() {
                    var x = 0
                }
            """, false
        )
    }

    @Test
    fun testSetContent(): Unit = ensureSetup {
        codegen(
            """
                fun fakeCompose(block: @Composable ()->Unit) { }

                class Test {
                    fun test() {
                        fakeCompose {
                            LinearLayout(orientation = LinearLayout.VERTICAL) {}
                        }
                    }
                }
            """
        )
    }

    @Test
    fun testComposeWithResult(): Unit = ensureSetup {
        compose(
            """
                @Composable fun <T> identity(block: @Composable ()->T): T = block()

                @Composable
                fun TestCall() {
                  val value: Any = identity { 12 }
                  TextView(text = value.toString(), id = 100)
                }
            """,
            noParameters,
            "TestCall()"
        ).then { activity ->
            val textView = activity.findViewById<TextView>(100)
            assertEquals("12", textView.text)
        }
    }

    @Test
    fun testObservable(): Unit = ensureSetup {
        compose(
            """
                import android.widget.Button
                import androidx.compose.*
                import androidx.ui.androidview.adapters.setOnClick

                @Model
                class FancyButtonData() {
                    var x = 0
                }

                @Composable
                fun SimpleComposable() {
                    FancyButton(state=FancyButtonData())
                }

                @Composable
                fun FancyButton(state: FancyButtonData) {
                    Button(text=("Clicked "+state.x+" times"), onClick={state.x++}, id=42)
                }
            """,
            noParameters,
            "SimpleComposable()"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Clicked 3 times", button.text)
        }
    }

    @Test
    fun testObservableLambda(): Unit = ensureSetup {
        compose(
            """
                @Model
                class FancyButtonCount() {
                    var count = 0
                }

                @Composable
                fun SimpleComposable(state: FancyButtonCount) {
                    FancyBox2 {
                        Button(
                          text=("Button clicked "+state.count+" times"),
                          onClick={state.count++},
                          id=42
                        )
                    }
                }

                @Composable
                fun FancyBox2(children: @Composable ()->Unit) {
                    children()
                }
            """,
            noParameters,
            "SimpleComposable(state=remember { FancyButtonCount() })"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Button clicked 3 times", button.text)
        }
    }

    @Test
    fun testObservableGenericFunction(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            @Composable
            fun <T> SimpleComposable(state: Counter, value: T) {
                Button(
                  text=("Button clicked "+state.count+" times: " + value),
                  onClick={state.count++},
                  id=42
                )
            }
        """,
            noParameters,
            "SimpleComposable(state=remember { Counter() }, value=\"Value\")"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Button clicked 3 times: Value", button.text)
        }
    }

    @Test
    fun testObservableExtension(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            @Composable
            fun Counter.Composable() {
                Button(
                    text="Button clicked "+count+" times",
                    onClick={count++},
                    id=42
                )
            }

            val myCounter = Counter()
            """,
            noParameters,
            "myCounter.Composable()"
        ).then { activity ->
            val button = activity.findViewById<Button>(42)
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById<Button>(42)
            assertEquals("Button clicked 3 times", button.text)
        }
    }

    @Test
    fun testObserverableExpressionBody(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            @Composable
            fun SimpleComposable(counter: Counter) =
                Button(
                    text="Button clicked "+counter.count+" times",
                    onClick={counter.count++},
                    id=42
                )

            @Composable
            fun SimpleWrapper(counter: Counter) = SimpleComposable(counter = counter)

            val myCounter = Counter()
            """,
            noParameters,
            "SimpleWrapper(counter = myCounter)"
        ).then { activity ->
            val button = activity.findViewById<Button>(42)
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById<Button>(42)
            assertEquals("Button clicked 3 times", button.text)
        }
    }

    @Test
    fun testObservableInlineWrapper(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            var inWrapper = false
            val counter = Counter()

            inline fun wrapper(block: () -> Unit) {
              inWrapper = true
              try {
                block()
              } finally {
                inWrapper = false
              }
            }

            @Composable
            fun SimpleComposable(state: Counter) {
                wrapper {
                    Button(
                      text=("Button clicked "+state.count+" times"),
                      onClick={state.count++},
                      id=42
                    )
                }
            }
        """,
            noParameters,
            "SimpleComposable(state=counter)"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Button clicked 3 times", button.text)
        }
    }

    @Test
    fun testObservableDefaultParameter(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            val counter = Counter()

            @Composable
            fun SimpleComposable(state: Counter, a: Int = 1, b: Int = 2) {
                Button(
                  text=("State: ${'$'}{state.count} a = ${'$'}a b = ${'$'}b"),
                  onClick={state.count++},
                  id=42
                )
            }
        """,
            noParameters,
            "SimpleComposable(state=counter, b = 4)"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("State: 3 a = 1 b = 4", button.text)
        }
    }

    @Test
    fun testObservableEarlyReturn(): Unit = ensureSetup {
        compose("""
            @Model
            class Counter() {
                var count = 0
            }

            val counter = Counter()

            @Composable
            fun SimpleComposable(state: Counter) {
                Button(
                  text=("State: ${'$'}{state.count}"),
                  onClick={state.count++},
                  id=42
                )

                if (state.count > 2) return

                TextView(
                  text="Included text",
                  id=43
                )
            }
        """,
            noParameters,
            "SimpleComposable(state=counter)"
        ).then { activity ->
            // Check that the text view is in the view
            assertNotNull(activity.findViewById(43))
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById<Button>(42)
            assertEquals("State: 3", button.text)

            // Assert that the text view is no longer in the view
            assertNull(activity.findViewById<Button>(43))
        }
    }

    @Test
    fun testCGSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                TextView(text="Hello, world!", id=42)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedFunction(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo() {
                    @Composable fun Bar() {
                        TextView(text="Hello, world!", id=42)
                    }
                    Bar()
                }
            """,
            noParameters,
            """
                Foo()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedExtensionFunction(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: String) {
                    @Composable fun String.Bar() {
                        TextView(text=this, id=42)
                    }
                    x.Bar()
                }
            """,
            noParameters,
            """
                Foo(x="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testImplicitReceiverScopeCall(): Unit = ensureSetup {
        compose(
            """
                import androidx.compose.*

                class Bar(val text: String)

                @Composable fun Bar.Foo() {
                    TextView(text=text,id=42)
                }

                @Composable
                fun Bam(bar: Bar) {
                    with(bar) {
                        Foo()
                    }
                }
            """,
            noParameters,
            """
                Bam(bar=Bar("Hello, world!"))
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedInvokeOperator(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: String) {
                    @Composable
                    operator fun String.invoke() {
                        TextView(text=this, id=42)
                    }
                    x()
                }
            """,
            noParameters,
            """
                Foo(x="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testTrivialExtensionFunction(): Unit = ensureSetup {
        compose(
            """ """,
            { mapOf<String, String>() },
            """
                val x = "Hello"
                @Composable fun String.foo() {}
                x.foo()
            """
        )
    }

    @Test
    fun testTrivialInvokeExtensionFunction(): Unit = ensureSetup {
        compose(
            """ """,
            noParameters,
            """
                val x = "Hello"
                @Composable operator fun String.invoke() {}
                x()
            """
        )
    }

    @Test
    fun testCGNSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                TextView(text="Hello, world!", id=42)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testInliningTemp(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: Double, children: @Composable Double.() -> Unit) {
                  x.children()
                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(x=1.0) {
                    TextView(text=this.toString(), id=123)
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(123) as TextView
            assertEquals("1.0", textView.text)
        }
    }

    @Test
    fun testInliningTemp2(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: Double.() -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then { }
    }

    @Test
    fun testInliningTemp3(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: (Double) -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then { }
    }

    @Test
    fun testInliningTemp4(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: (Double) -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then {}
    }

    @Test
    fun testInline_NonComposable_Identity(): Unit = ensureSetup {
        compose("""
            @Composable inline fun InlineWrapper(base: Int, children: @Composable ()->Unit) {
              children()
            }
            """,
            noParameters,
            """
            InlineWrapper(200) {
              TextView(text = "Test", id=101)
            }
            """).then { activity ->
            assertEquals("Test", activity.findViewById<TextView>(101).text)
        }
    }

    @Test
    fun testInline_Composable_Identity(): Unit = ensureSetup {
        compose("""
            @Composable
            inline fun InlineWrapper(base: Int, children: @Composable ()->Unit) {
              children()
            }
            """,
            noParameters,
            """
            InlineWrapper(200) {
                TextView(text = "Test", id=101)
            }
            """).then { activity ->
            assertEquals("Test", activity.findViewById<TextView>(101).text)
        }
    }

    @Test
    fun testInline_Composable_EmitChildren(): Unit = ensureSetup {
        compose("""
            @Composable
            inline fun InlineWrapper(base: Int, crossinline children: @Composable ()->Unit) {
              LinearLayout(id = base + 0) {
                children()
              }
            }
            """,
            noParameters,
            """
            InlineWrapper(200) {
              TextView(text = "Test", id=101)
            }
            """).then { activity ->
            val tv = activity.findViewById<TextView>(101)
            // Assert the TextView was created with the correct text
            assertEquals("Test", tv.text)
            // and it is the first child of the linear layout
            assertEquals(tv, activity.findViewById<LinearLayout>(200).getChildAt(0))
        }
    }

    @Test
    fun testCGNInlining(): Unit = ensureSetup {
        compose(
            """
                LinearLayout(orientation=LinearLayout.VERTICAL) {
                    TextView(text="Hello, world!", id=42)
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGUpdatedComposition(): Unit = ensureSetup {
        var value = "Hello, world!"

        compose(
            { mapOf("value" to value) }, """
           TextView(text=value, id=42)
        """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)

            value = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Other value", textView.text)
        }
    }

    @Test
    fun testCGNUpdatedComposition(): Unit = ensureSetup {
        var value = "Hello, world!"

        compose(
            { mapOf("value" to value) }, """
           TextView(text=value, id=42)
        """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)

            value = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Other value", textView.text)
        }
    }

    @Test
    fun testCGViewGroup(): Unit = ensureSetup {
        val tvId = 258
        val llId = 260
        var text = "Hello, world!"
        var orientation = LinearLayout.HORIZONTAL

        compose(
            { mapOf("text" to text, "orientation" to orientation) }, """
            LinearLayout(orientation=orientation, id=$llId) {
              TextView(text=text, id=$tvId)
            }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)

            text = "Other value"
            orientation = LinearLayout.VERTICAL
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)
        }
    }

    // @Test
    fun testCGNAmbient(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """

            val StringAmbient = ambientOf<String> { "default" }

            @Composable fun Foo() {
                TextView(id=$tvId, text=StringAmbient.current)
            }

        """,
            { mapOf("text" to text) },
            """
            Providers(StringAmbient provides text) {
                Foo()
            }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    @Test
    fun testCGNFunctionComponent(): Unit = ensureSetup {
        var text = "Hello, world!"
        val tvId = 123

        compose(
            """
            @Composable
            fun Foo(text: String) {
                TextView(id=$tvId, text=text)
            }

        """,
            { mapOf("text" to text) },
            """
             Foo(text=text)
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testAmbientReference(): Unit = ensureSetup {
        val outerId = 123
        val innerId = 345
        val buttonId = 456

        compose(
            """
                fun buildPortal() = effectOf<Ambient.Reference> {
                    context.buildReference()
                }

                fun <T> refFor() = memo { Ref<T>() }

                val textAmbient = Ambient.of { "default" }

                @Composable fun DisplayTest(id: Int) {
                    val text = ambient(textAmbient)
                    TextView(id=id, text=text)
                }

                @Composable fun PortalTest() {
                    val portal = +buildPortal()
                    val ref = +refFor<LinearLayout>()
                    DisplayTest(id=$outerId)

                    LinearLayout(ref=ref)

                    val root = ref.value ?: error("Expected a linear")

                    composeInto(root, portal) {
                        DisplayTest(id=$innerId)
                    }
                }

                @Composable
                fun TestApp() {
                    val inc = state { 1 }

                    Button(id=$buttonId, text="Click Me", onClick={ inc.value += 1 })

                    textAmbient.Provider(value="value: ${"$"}{inc.value}") {
                        PortalTest()
                    }
                }
            """,
            { mapOf("text" to "") },
            """
                TestApp()
            """
        ).then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView
            val button = activity.findViewById(buttonId) as Button

            assertEquals("inner", "value: 1", inner.text)
            assertEquals("outer", "value: 1", outer.text)

            button.performClick()
        }.then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView
            val button = activity.findViewById(buttonId) as Button

            assertEquals("inner", "value: 2", inner.text)
            assertEquals("outer", "value: 2", outer.text)

            button.performClick()
        }.then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView

            assertEquals("inner", "value: 3", inner.text)
            assertEquals("outer", "value: 3", outer.text)
        }
    }

    @Ignore("Test case for b/143464846 - re-enable when bug is fixed.")
    @Test
    fun testAmbientConsumedFromDefaultParameter(): Unit = ensureSetup {
        val initialText = "no text"
        val helloWorld = "Hello World!"
        compose("""
            val TextAmbient = Ambient.of { "$initialText" }

            @Composable
            fun Main() {
                var text = state { "$initialText" }
                Providers(TextAmbient provides text.value) {
                    LinearLayout {
                        ConsumesAmbientFromDefaultParameter()
                        Button(
                            text = "Change ambient value",
                            onClick={ text.value = "$helloWorld" },
                            id=101
                        )
                    }
                }
            }

            @Composable
            fun ConsumesAmbientFromDefaultParameter(text: String = TextAmbient.current) {
                TextView(text = text, id = 42)
            }
        """,
            noParameters,
            "Main()"
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals(initialText, textView.text)
        }.then { activity ->
            val button = activity.findViewById(101) as Button
            button.performClick()
        }
        .then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals(helloWorld, textView.text)
        }
    }

    @Test
    fun testCGNViewGroup(): Unit = ensureSetup {
        val tvId = 258
        val llId = 260
        var text = "Hello, world!"
        var orientation = LinearLayout.HORIZONTAL

        compose(
            { mapOf("text" to text, "orientation" to orientation) }, """
             LinearLayout(orientation=orientation, id=$llId) {
               TextView(text=text, id=$tvId)
             }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)

            text = "Other value"
            orientation = LinearLayout.VERTICAL
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)
        }
    }

    @Test
    fun testMemoization(): Unit = ensureSetup {
        val tvId = 258
        val tagId = (3 shl 24) or "composed_set".hashCode()

        compose(
            """
                var composedSet = mutableSetOf<String>()
                var inc = 1

                fun View.setComposed(composed: Set<String>) = setTag($tagId, composed)

                @Composable fun ComposePrimitive(value: Int) {
                    composedSet.add("ComposePrimitive(" + value + ")")
                }

                class MutableThing(var value: String)

                val constantMutableThing = MutableThing("const")

                @Composable fun ComposeMutable(value: MutableThing) {
                    composedSet.add("ComposeMutable(" + value.value + ")")
                }
            """,
            { mapOf("text" to "") },
            """
                composedSet.clear()

                ComposePrimitive(value=123)
                ComposePrimitive(value=inc)
                ComposeMutable(value=constantMutableThing)
                ComposeMutable(value=MutableThing("new"))

                TextView(id=$tvId, composed=composedSet)

                inc++
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId) ?: error(
                "expected a compose set to exist")

            fun assertContains(contains: Boolean, key: String) {
                assertEquals("composedSet contains key '$key'", contains, composedSet.contains(key))
            }

            assertContains(true, "ComposePrimitive(123)")
            assertContains(true, "ComposePrimitive(1)")
            assertContains(true, "ComposeMutable(const)")
            assertContains(true, "ComposeMutable(new)")
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId)
                ?: error("expected a compose set to exist")

            fun assertContains(contains: Boolean, key: String) {
                assertEquals("composedSet contains key '$key'", contains, composedSet.contains(key))
            }

            // the primitive component skips based on equality
            assertContains(false, "ComposePrimitive(123)")

            // since the primitive changed, this one recomposes again
            assertContains(true, "ComposePrimitive(2)")

            // since this is a potentially mutable object, we don't skip based on it
            assertContains(true, "ComposeMutable(const)")

            // since its a new one every time, we definitely don't skip
            assertContains(true, "ComposeMutable(new)")
        }
    }

    @Test
    fun testInlineClassMemoization(): Unit = ensureSetup {
        val tvId = 258
        val tagId = (3 shl 24) or "composed_set".hashCode()

        compose(
            """
                inline class InlineInt(val value: Int)
                inline class InlineInlineInt(val value: InlineInt)
                inline class InlineMutableSet(val value: MutableSet<String>)
                fun View.setComposed(composed: Set<String>) = setTag($tagId, composed)

                val composedSet = mutableSetOf<String>()
                val constInlineInt = InlineInt(0)
                var inc = 2
                val constInlineMutableSet = InlineMutableSet(mutableSetOf("a"))

                @Composable fun ComposedInlineInt(value: InlineInt) {
                  composedSet.add("ComposedInlineInt(" + value + ")")
                }

                @Composable fun ComposedInlineInlineInt(value: InlineInlineInt) {
                  composedSet.add("ComposedInlineInlineInt(" + value + ")")
                }

                @Composable fun ComposedInlineMutableSet(value: InlineMutableSet) {
                  composedSet.add("ComposedInlineMutableSet(" + value + ")")
                }
            """,
            { mapOf("text" to "") },
            """
                composedSet.clear()

                ComposedInlineInt(constInlineInt)
                ComposedInlineInt(InlineInt(1))
                ComposedInlineInt(InlineInt(inc))
                ComposedInlineInlineInt(InlineInlineInt(InlineInt(2)))
                ComposedInlineMutableSet(constInlineMutableSet)

                TextView(id=$tvId, composed=composedSet)

                inc++
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId)
                ?: error("expected a compose set to exist")

            // All composables should execute since it's the first time.
            assert(composedSet.contains("ComposedInlineInt(InlineInt(value=0))"))
            assert(composedSet.contains("ComposedInlineInt(InlineInt(value=1))"))
            assert(composedSet.contains("ComposedInlineInt(InlineInt(value=2))"))
            assert(composedSet.contains(
                "ComposedInlineInlineInt(InlineInlineInt(value=InlineInt(value=2)))"))
            assert(composedSet.contains("ComposedInlineMutableSet(InlineMutableSet(value=[a]))"))
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId)
                ?: error("expected a compose set to exist")

            // InlineInt and InlineInlineInt are stable, so the corresponding composables should
            // not run for values equal to previous compositions.
            assert(!composedSet.contains("ComposedInlineInt(InlineInt(value=0))"))
            assert(!composedSet.contains("ComposedInlineInt(InlineInt(value=1))"))
            assert(!composedSet.contains(
                "ComposedInlineInlineInt(InlineInlineInt(value=InlineInt(value=2)))"))

            // But if a stable composable is passed a new value, it should re-run.
            assert(composedSet.contains("ComposedInlineInt(InlineInt(value=3))"))

            // And composables for inline classes with non-stable underlying types should run.
            assert(composedSet.contains("ComposedInlineMutableSet(InlineMutableSet(value=[a]))"))
        }
    }

    @Test
    fun testStringParameterMemoization(): Unit = ensureSetup {
        val tvId = 258
        val tagId = (3 shl 24) or "composed_set".hashCode()

        compose(
            """
                fun View.setComposed(composed: Set<String>) = setTag($tagId, composed)

                val composedSet = mutableSetOf<String>()
                val FOO = "foo"

                @Composable fun ComposedString(value: String) {
                  composedSet.add("ComposedString(" + value + ")")
                }
            """,
            { mapOf("text" to "") },
            """
                composedSet.clear()

                ComposedString(FOO)
                ComposedString("bar")

                TextView(id=$tvId, composed=composedSet)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId)
                ?: error("expected a compose set to exist")

            // All composables should execute since it's the first time.
            assert(composedSet.contains("ComposedString(foo)"))
            assert(composedSet.contains("ComposedString(bar)"))
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet(tagId)
                ?: error("expected a compose set to exist")

            assert(composedSet.isEmpty())
        }
    }

    @Test
    fun testCGNSimpleCall(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """
                @Composable fun SomeFun(x: String) {
                    TextView(text=x, id=$tvId)
                }
            """,
            { mapOf("text" to text) },
            """
                SomeFun(x=text)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)

            text = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testCGNSimpleCall2(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"
        var someInt = 456

        compose(
            """
                class SomeClass(var x: String) {
                    @Composable
                    operator fun invoke(y: Int) {
                        TextView(text="${"$"}x ${"$"}y", id=$tvId)
                    }
                }
            """,
            { mapOf("text" to text, "someInt" to someInt) },
            """
                SomeClass(x=text, y=someInt)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Hello, world! 456", textView.text)

            text = "Other value"
            someInt = 123
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Other value 123", textView.text)
        }
    }

    @Test
    fun testCGNCallWithChildren(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """
                @Composable
                fun Block(children: @Composable () -> Unit) {
                    children()
                }
            """,
            { mapOf("text" to text) },
            """
                Block {
                    Block {
                        TextView(text=text, id=$tvId)
                    }
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)

            text = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testCGNStuff(): Unit = ensureSetup {
        val tvId = 258
        var num = 123

        compose(
            """
                class OneArg {
                    var foo = 0
                    @Composable
                    operator fun invoke() {
                        TextView(text="${"$"}foo", id=$tvId)
                    }
                }
                fun OneArg.setBar(bar: Int) { foo = bar }
            """,
            { mapOf("num" to num) },
            """
            OneArg(bar=num)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals("$num", textView.text)

            num = 456
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals("$num", textView.text)
        }
    }

    // @Test
    fun testTagBasedMemoization(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello World"

        compose(
            """
                class A {
                    var foo = ""
                    inner class B {
                        @Composable
                        operator fun invoke() {
                            TextView(text=foo, id=$tvId)
                        }
                    }
                }
            """,
            { mapOf("text" to text) },
            """
                val a = A()
                a.foo = text
                a.B()
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(text, textView.text)

            text = "new value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(text, textView.text)
        }
    }

    @Test
    fun testCGComposableFunctionInvocationOneParameter(): Unit = ensureSetup {
        val tvId = 91
        var phone = "(123) 456-7890"
        compose(
            """
           @Composable
           fun Phone(value: String) {
             TextView(text=value, id=$tvId)
           }
        """, { mapOf("phone" to phone) }, """
           Phone(value=phone)
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(phone, textView.text)

            phone = "(123) 456-7899"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(phone, textView.text)
        }
    }

    @Test
    fun testCGComposableFunctionInvocationTwoParameters(): Unit = ensureSetup {
        val tvId = 111
        val rsId = 112
        var left = 0
        var right = 0
        compose(
            """
           var addCalled = 0

           @Composable
           fun AddView(left: Int, right: Int) {
             addCalled++
             TextView(text="${'$'}left + ${'$'}right = ${'$'}{left + right}", id=$tvId)
             TextView(text="${'$'}addCalled", id=$rsId)
           }
        """, { mapOf("left" to left, "right" to right) }, """
           AddView(left=left, right=right)
        """
        ).then { activity ->
            // Should be called on the first compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )
        }.then { activity ->
            // Should be skipped on the second compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )

            left = 1
        }.then { activity ->
            // Should be called again because left changed.
            assertEquals("2", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )

            right = 41
        }.then { activity ->
            // Should be called again because right changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )
        }.then { activity ->
            // Should be skipped because nothing changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
        }
    }

    @Test
    fun testImplicitReceiverPassing1(): Unit = ensureSetup {
        compose(
            """
                @Composable fun Int.Foo(x: @Composable Int.() -> Unit) {
                    x()
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                id.Foo(x={
                    TextView(text="Hello, world!", id=this)
                })
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testImplicitReceiverPassing2(): Unit = ensureSetup {
        compose(
            """
                @Composable fun Int.Foo(x: @Composable Int.(text: String) -> Unit, text: String) {
                    x(text=text)
                }

                @Composable fun MyText(text: String, id: Int) {
                    TextView(text=text, id=id)
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                id.Foo(text="Hello, world!", x={ text ->
                    MyText(text=text, id=this)
                })
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testEffects1(): Unit = ensureSetup {
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Composable
                fun Counter() {
                    Observe {
                        var count = state { 0 }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                Counter()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            textView.performClick()
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 1", textView.text)
        }
    }

    @Test
    fun testEffects2(): Unit = ensureSetup {
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Model class MyState<T>(var value: T)

                @Composable
                fun Counter() {
                    Observe {
                        var count = remember { MyState(0) }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                Counter()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            textView.performClick()
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 1", textView.text)
        }
    }

    @Test
    fun testEffects3(): Unit = ensureSetup {
        val log = StringBuilder()
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Composable
                fun Counter(log: StringBuilder) {
                    Observe {
                        var count = state { 0 }
                        onCommit {
                            log.append("a")
                        }
                        onActive {
                            log.append("b")
                        }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf("log" to log) },
            """
                Counter(log=log)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            assertEquals("ab", log.toString())

            execute {
                textView.performClick()
            }

            assertEquals("Count: 1", textView.text)
            assertEquals("aba", log.toString())
        }
    }

    @Test
    fun testEffects4(): Unit = ensureSetup {
        val log = StringBuilder()
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Composable
                fun printer(log: StringBuilder, str: String) {
                    onCommit {
                        log.append(str)
                    }
                }

                @Composable
                fun Counter(log: StringBuilder) {
                    Observe {
                        var count = state { 0 }
                        printer(log, "" + count.value)
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf("log" to log) },
            """
                Counter(log=log)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            assertEquals("0", log.toString())

            execute {
                textView.performClick()
            }

            assertEquals("Count: 1", textView.text)
            assertEquals("01", log.toString())
        }
    }

    @Test
    fun testVariableCalls1(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
            """,
            { mapOf<String, String>() },
            """
                component()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testVariableCalls2(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
                class HolderA(val composable: @Composable () -> Unit)

                val holder = HolderA(component)

            """,
            { mapOf<String, String>() },
            """
                holder.composable()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testVariableCalls3(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
                class HolderB(val composable: @Composable () -> Unit) {
                    @Composable
                    fun Foo() {
                        composable()
                    }
                }

                val holder = HolderB(component)

            """,
            { mapOf<String, String>() },
            """
                holder.Foo()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    // b/123721921
    @Test
    fun testDefaultParameters1(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(a: Int = 42, b: String) {
                    TextView(text=b, id=a)
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(b="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testDefaultParameters2(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(a: Int = 42, b: String, c: @Composable () -> Unit) {
                    c()
                    TextView(text=b, id=a)
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(b="Hello, world!") {}
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testMovement(): Unit = ensureSetup {
        val tvId = 50
        val btnIdAdd = 100
        val btnIdUp = 200
        val btnIdDown = 300

        // Duplicate the steps to reproduce an issue discovered in the Reorder example
        compose(
            """
            fun <T> List<T>.move(from: Int, to: Int): List<T> {
                if (to < from) return move(to, from)
                val item = get(from)
                val currentItem = get(to)
                val left = if (from > 0) subList(0, from) else emptyList()
                val right = if (to < size) subList(to + 1, size) else emptyList()
                val middle = if (to - from > 1) subList(from + 1, to) else emptyList()
                return left + listOf(currentItem) + middle + listOf(item) + right
            }

            @Composable
            fun Reordering() {
                Observe {
                    val items = state { listOf(1, 2, 3, 4, 5) }

                    LinearLayout(orientation=LinearLayout.VERTICAL) {
                        items.value.forEachIndexed { index, id ->
                            Item(
                                id=id,
                                onMove={ amount ->
                                    val next = index + amount
                                    if (next >= 0 && next < items.value.size) {
                                        items.value = items.value.move(index, index + amount)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            @Composable
            // TODO: Investigate making this private; looks like perhaps a compiler bug as of rebase
            fun Item(@Pivotal id: Int, onMove: (Int) -> Unit) {
                Observe {
                    val count = state { 0 }
                    LinearLayout(orientation=LinearLayout.HORIZONTAL) {
                        TextView(id=(id+$tvId), text="id: ${'$'}id amt: ${'$'}{count.value}")
                        Button(id=(id+$btnIdAdd), text="+", onClick={ count.value++ })
                        Button(id=(id+$btnIdUp), text="Up", onClick={ onMove(1) })
                        Button(id=(id+$btnIdDown), text="Down", onClick={ onMove(-1) })
                    }
                }
            }
            """, noParameters,
            """
               Reordering()
            """
        ).then { activity ->
            // Click 5 add
            val button = activity.findViewById(btnIdAdd + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 down
            val button = activity.findViewById(btnIdDown + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 down
            val button = activity.findViewById(btnIdDown + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 up
            val button = activity.findViewById(btnIdUp + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 up
            val button = activity.findViewById(btnIdUp + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 add
            val button = activity.findViewById(btnIdAdd + 5) as Button
            button.performClick()
        }.then { activity ->
            val textView = activity.findViewById(tvId + 5) as TextView
            assertEquals("id: 5 amt: 2", textView.text)
        }
    }

    @Test
    fun testObserveKtxWithInline(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun SimpleComposable() {
                    val count = state { 1 }
                    Box {
                        repeat(count.value) {
                            Button(text="Increment", onClick={ count.value += 1 }, id=(41+it))
                        }
                    }
                }

                @Composable
                fun Box(children: @Composable ()->Unit) {
                    LinearLayout(orientation=LinearLayout.VERTICAL) {
                        children()
                    }
                }
            """, noParameters,
            """
               SimpleComposable()
            """
        ).then { activity ->
            val button = activity.findViewById(41) as Button
            button.performClick()
            button.performClick()
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            assertNotNull(activity.findViewById(46))
        }
    }

    @Test
    fun testKeyTag(): Unit = ensureSetup {
        compose(
            """
            val list = mutableListOf(0,1,2,3)

            @Composable
            fun Reordering() {
                LinearLayout {
                    Recompose { recompose ->
                        Button(
                          id=50,
                          text="Recompose!",
                          onClick={ list.add(list.removeAt(0)); recompose(); }
                        )
                        LinearLayout(id=100) {
                            for(id in list) {
                                key(v1=id) {
                                    StatefulButton()
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            // TODO: Investigate making this private; looks like perhaps a compiler bug as of rebase
            fun StatefulButton() {
                val count = state { 0 }
                Button(text="Clicked ${'$'}{count.value} times!", onClick={ count.value++ })
            }
            """, noParameters,
            """
               Reordering()
            """
        ).then { activity ->
            val layout = activity.findViewById(100) as LinearLayout
            layout.getChildAt(0).performClick()
        }.then { activity ->
            val recomposeButton = activity.findViewById(50) as Button
            recomposeButton.performClick()
        }.then { activity ->
            val layout = activity.findViewById(100) as LinearLayout
            assertEquals("Clicked 0 times!", (layout.getChildAt(0) as Button).text)
            assertEquals("Clicked 0 times!", (layout.getChildAt(1) as Button).text)
            assertEquals("Clicked 0 times!", (layout.getChildAt(2) as Button).text)
            assertEquals("Clicked 1 times!", (layout.getChildAt(3) as Button).text)
        }
    }

    @Test
    fun testNonFcs(): Unit = ensureSetup {
        compose(
            """

            class MyTextView(context: Context) : TextView(context) {}

            fun foo(context: Context): TextView = MyTextView(context=context)

            """, noParameters,
            """
            """
        )
    }

    @Test
    fun testNonComposeParameters(): Unit = ensureSetup {
        compose(
            """
                class Action(
                   val s: String = "",
                   val param: Int,
                   type: Set<Int> = setOf(),
                   val action: () -> Unit
                )

                @Composable
                fun DefineAction(
                    onAction: Action = Action(param = 1) {},
                    children: @Composable ()->Unit
                 ) { }
            """
        )
    }

    @Test
    fun testStableParameters_Various(): Unit = ensureSetup {
        val output = ArrayList<String>()
        compose("""
            @Model
            class M { var count = 0 }
            val m = M()

            @Immutable
            data class ValueHolder(val value: Int)

            var output = ArrayList<String>()

            class NotStable { val value = 10 }

            @Stable
            class StableClass {
                override fun equals(other: Any?) = true
            }

            enum class EnumState {
              One,
              Two
            }

            val mutableStateType = mutableStateOf(1)
            val stateType: State<Int> = mutableStateType

            @Composable
            fun MemoInt(a: Int) {
              output.add("MemoInt a=${'$'}a")
              Button(id=101, text="memo ${'$'}a", onClick={ m.count++ })
            }

            @Composable
            fun MemoFloat(a: Float) {
              output.add("MemoFloat")
              Button(text="memo ${'$'}a")
            }

            @Composable
            fun MemoDouble(a: Double) {
              output.add("MemoDouble")
              Button(text="memo ${'$'}a")
            }

            @Composable
            fun MemoNotStable(a: NotStable) {
              output.add("MemoNotStable")
              Button(text="memo ${'$'}{a.value}")
            }

            @Composable
            fun MemoModel(a: ValueHolder) {
              output.add("MemoModelHolder")
              Button(text="memo ${'$'}{a.value}")
            }

            @Composable
            fun MemoEnum(a: EnumState) {
              output.add("MemoEnum")
              Button(text="memo ${'$'}{a}")
            }

            @Composable
            fun MemoStable(a: StableClass) {
              output.add("MemoStable")
              Button(text="memo stable")
            }

            @Composable
            fun MemoMutableState(a: MutableState<Int>) {
              output.add("MemoMutableState")
              Button(text="memo ${'$'}{a.value}")
            }

            @Composable
            fun MemoState(a: State<Int>) {
              output.add("MemoState")
              Button(text="memo ${'$'}{a.value}")
            }

            @Composable
            fun TestSkipping(
                a: Int,
                b: Float,
                c: Double,
                d: NotStable,
                e: ValueHolder,
                f: EnumState,
                g: StableClass,
                h: MutableState<Int>,
                i: State<Int>
            ) {
              val am = a + m.count
              output.add("TestSkipping a=${'$'}a am=${'$'}am")
              MemoInt(a=am)
              MemoFloat(a=b)
              MemoDouble(a=c)
              MemoNotStable(a=d)
              MemoModel(a=e)
              MemoEnum(a=f)
              MemoStable(a=g)
              MemoMutableState(h)
              MemoState(i)
            }

            @Composable
            fun Main(v: ValueHolder, n: NotStable) {
              TestSkipping(
                a=1,
                b=1f,
                c=2.0,
                d=NotStable(),
                e=v,
                f=EnumState.One,
                g=StableClass(),
                h=mutableStateType,
                i=stateType
              )
            }
        """, {
            mapOf(
                "outerOutput: ArrayList<String>" to output
            )
        }, """
            output = outerOutput
            val v = ValueHolder(0)
            Main(v, NotStable())
        """).then {
            // Expect that all the methods are called in order
            assertEquals(
                "TestSkipping a=1 am=1, MemoInt a=1, MemoFloat, " +
                        "MemoDouble, MemoNotStable, MemoModelHolder, MemoEnum, MemoStable, " +
                        "MemoMutableState, MemoState",
                output.joinToString()
            )
            output.clear()
        }.then { activity ->
            // Expect TestSkipping and MemoNotStable to be called because the test forces an extra compose.
            assertEquals("TestSkipping a=1 am=1, MemoNotStable", output.joinToString())
            output.clear()

            // Change the model
            val button = activity.findViewById(101) as Button
            button.performClick()
        }.then {
            // Expect that only MemoInt (the parameter changed) and MemoNotStable (it has unstable parameters) were
            // called then expect a second compose which should only MemoNotStable
            assertEquals(
                "TestSkipping a=1 am=2, MemoInt a=2, MemoNotStable, " +
                        "TestSkipping a=1 am=2, MemoNotStable",
                output.joinToString()
            )
        }
    }

    @Test
    fun testStableParameters_Lambdas(): Unit = ensureSetup {
        val output = ArrayList<String>()
        compose("""
            @Model
            class M { var count = 0 }
            val m = M()

            var output = ArrayList<String>()
            val unchanged: () -> Unit = { }

            fun log(msg: String) { output.add(msg) }

            @Composable
            fun Container(children: @Composable () -> Unit) {
              log("Container")
              children()
            }

            @Composable
            fun NormalLambda(index: Int, lambda: () -> Unit) {
              log("NormalLambda(${'$'}index)")
              Button(text="text")
            }

            @Composable
            fun TestSkipping(unchanged: () -> Unit, changed: () -> Unit) {
              log("TestSkipping")
              Container {
                NormalLambda(index = 1, lambda = unchanged)
                NormalLambda(index = 2, lambda = unchanged)
                NormalLambda(index = 3, lambda = unchanged)
                NormalLambda(index = 4, lambda = changed)
              }
            }

            fun forceNewLambda(): () -> Unit = { }

            @Composable
            fun Main(unchanged: () -> Unit) {
              Button(id=101, text="model ${'$'}{m.count}", onClick={ m.count++ })
              TestSkipping(unchanged = unchanged, changed = forceNewLambda())
            }
        """, {
            mapOf(
                "outerOutput: ArrayList<String>" to output
            )
        }, """
            output = outerOutput
            Main(unchanged = unchanged)
        """).then {
            // Expect that all the methods are called in order
            assertEquals(
                "TestSkipping, Container, NormalLambda(1), " +
                        "NormalLambda(2), NormalLambda(3), NormalLambda(4)",
                output.joinToString()
            )
            output.clear()
        }.then { activity ->
            // Expect nothing to occur with no changes
            assertEquals("", output.joinToString())
            output.clear()

            // Change the model
            val button = activity.findViewById(101) as Button
            button.performClick()
        }.then {
            // Expect only NormalLambda(4) to be called
            assertEquals(
                "TestSkipping, NormalLambda(4)",
                output.joinToString()
            )
        }
    }

    @Test
    fun testRecomposeScope(): Unit = ensureSetup {
        compose("""
             @Model
            class M { var count = 0 }
            val m = M()

            @Composable
            inline fun InlineContainer(children: @Composable () -> Unit) {
                children()
            }

            @Composable
            fun Container(children: @Composable () -> Unit) {
                children()
            }

            @Composable
            fun Leaf(v: Int) {}

            @Composable
            fun Inline() {
                InlineContainer {
                    Leaf(v = 1)
                }
            }

            @Composable
            fun Lambda() {
                val a = 1
                val b = 2
                Container {
                    TextView(text = "value = ${'$'}{m.count}", id = 100)
                    Leaf(v = 1)
                    Leaf(v = a)
                    Leaf(v = b)
                }
            }
            """,
            noParameters,
            """
                Button(id=101, text="model ${'$'}{m.count}", onClick={ m.count++ })
                Lambda()
            """
        ).then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "value = 0")
            val button = activity.findViewById<Button>(101)
            button.performClick()
        }.then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "value = 1")
        }
    }

    @Test
    fun testRecomposeScope_ReceiverScope(): Unit = ensureSetup {
        compose("""
            @Model
            class M { var count = 0 }
            val m = M()

            class Receiver { var r: Int = 0 }

            @Composable
            fun Container(children: @Composable Receiver.() -> Unit) {
                Receiver().children()
            }

            @Composable
            fun Lambda() {
                Container {
                    TextView(text = "value = ${'$'}{m.count}", id = 100)
                }
            }
            """,
            noParameters,
            """
                Button(id=101, text="model ${'$'}{m.count}", onClick={ m.count++ })
                Lambda()
            """
        ).then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "value = 0")
            val button = activity.findViewById<Button>(101)
            button.performClick()
        }.then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "value = 1")
        }
    }

    @Test
    fun testRecomposeScope_Method(): Unit = ensureSetup {
        compose("""
            @Model
            class M { var count = 0 }
            val m = M()

            @Composable
            fun Leaf() { }

            class SelfCompose {
                var f1 = 0

                @Composable
                fun compose(f2: Int) {
                    TextView(
                      text = "f1=${'$'}f1, f2=${'$'}f2, m=${'$'}{m.count*f1*f2}",
                      id = 100
                    )
                }
            }

            @Composable
            fun InvokeSelfCompose() {
                val r = remember() { SelfCompose() }
                r.f1 = 1
                r.compose(f2 = 10)
                Leaf()
            }
            """,
            noParameters,
            """
                Button(id=101, text="model ${'$'}{m.count}", onClick={ m.count++ })
                InvokeSelfCompose()
            """
        ).then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "f1=1, f2=10, m=0")
            val button = activity.findViewById<Button>(101)
            button.performClick()
        }.then { activity ->
            assertEquals(activity.findViewById<TextView>(100).text, "f1=1, f2=10, m=10")
        }
    }

    fun codegen(text: String, dumpClasses: Boolean = false) {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        classLoader(
            """
           import android.content.Context
           import android.widget.*
           import androidx.compose.*

           $text

        """, fileName, dumpClasses
        )
    }

    fun compose(text: String, dumpClasses: Boolean = false): RobolectricComposeTester = compose(
        { mapOf<String, Any>() },
        text,
        dumpClasses
    )

    fun <T : Any> compose(
        valuesFactory: () -> Map<String, T>,
        text: String,
        dumpClasses: Boolean = false
    ) = compose("", valuesFactory, text, dumpClasses)

    private fun execute(block: () -> Unit) {
        val scheduler = RuntimeEnvironment.getMasterScheduler()
        scheduler.pause()
        block()
        scheduler.advanceToLastPostedRunnable()
    }

    fun <T : Any> compose(
        prefix: String,
        valuesFactory: () -> Map<String, T>,
        text: String,
        dumpClasses: Boolean = false
    ): RobolectricComposeTester {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        val candidateValues = valuesFactory()

        @Suppress("NO_REFLECTION_IN_CLASS_PATH")
        val parameterList = candidateValues.map {
            if (it.key.contains(':')) {
                it.key
            } else "${it.key}: ${it.value::class.qualifiedName}"
        }.joinToString()
        val parameterTypes = candidateValues.map {
            it.value::class.javaPrimitiveType ?: it.value::class.javaObjectType
        }.toTypedArray()

        val compiledClasses = classLoader(
            """
           import android.content.Context
           import android.widget.*
           import android.view.View
           import androidx.compose.*
           import androidx.ui.androidview.adapters.*

           $prefix

           class $className {

             @Composable
             fun test($parameterList) {
               $text
             }
           }
        """, fileName, dumpClasses
        )

        val allClassFiles = compiledClasses.allGeneratedFiles.filter {
            it.relativePath.endsWith(".class")
        }

        val loader = URLClassLoader(emptyArray(), this.javaClass.classLoader)

        val instanceClass = run {
            var instanceClass: Class<*>? = null
            var loadedOne = false
            for (outFile in allClassFiles) {
                val bytes = outFile.asByteArray()
                val loadedClass = loadClass(loader, null, bytes)
                if (loadedClass.name == className) instanceClass = loadedClass
                loadedOne = true
            }
            if (!loadedOne) error("No classes loaded")
            instanceClass ?: error("Could not find class $className in loaded classes")
        }

        val instanceOfClass = instanceClass.newInstance()
        val testMethod = instanceClass.getMethod("test", *parameterTypes, Composer::class.java)

        return compose {
            val values = valuesFactory()
            val arguments = values.map { it.value as Any }.toTypedArray()
            testMethod.invoke(instanceOfClass, *arguments, it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun View.getComposedSet(tagId: Int): Set<String>? = getTag(tagId) as? Set<String>
}
