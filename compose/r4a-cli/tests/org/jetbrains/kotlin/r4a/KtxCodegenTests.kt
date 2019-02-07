package org.jetbrains.kotlin.r4a

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.r4a.Component
import com.google.r4a.CompositionContext
import com.google.r4a.composer

import org.jetbrains.kotlin.extensions.KtxControlFlowExtension
import org.jetbrains.kotlin.extensions.KtxTypeResolutionExtension
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.extensions.TypeResolutionInterceptorExtension
import org.jetbrains.kotlin.parsing.KtxParsingExtension
import org.jetbrains.kotlin.psi2ir.extensions.SyntheticIrExtension
import org.jetbrains.kotlin.r4a.frames.FrameTransformExtension
import org.jetbrains.kotlin.r4a.frames.analysis.FrameModelChecker
import org.jetbrains.kotlin.r4a.frames.analysis.PackageAnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URLClassLoader

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    minSdk = 23,
    maxSdk = 23
)
class KtxCodegenTests : AbstractCodeGenTest() {

    @Test
    fun testCGSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                <TextView text="Hello, world!" id=42 />
            """).then { activity ->
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
                        <TextView text="Hello, world!" id=42 />
                    }
                    <Bar />
                }
            """,
            { mapOf<String,String>() },
            """
                <Foo />
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
                        <TextView text=this id=42 />
                    }
                    <x.Bar />
                }
            """,
            { mapOf<String, String>() },
            """
                <Foo x="Hello, world!" />
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
                import com.google.r4a.*

                class Bar(val text: String)

                @Composable fun Bar.Foo() {
                    <TextView text=text id=42 />
                }

                @Composable
                fun Bam(bar: Bar) {
                    with(bar) {
                        <Foo />
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                <Bam bar=Bar("Hello, world!") />
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
                    @Composable operator fun String.invoke() {
                        <TextView text=this id=42 />
                    }
                    <x />
                }
            """,
            { mapOf<String, String>() },
            """
                <Foo x="Hello, world!" />
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGNSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                <TextView text="Hello, world!" id=42 />
            """).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testInliningTemp(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: Double, @Children children: Double.() -> Unit) {
                  <x.children />
                }
            """,
            { mapOf("foo" to "bar") },
            """
                <Foo x=1.0>
                    <TextView text=this.toString() id=123 />
                </Foo>
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
                <Foo onClick={} />
            """
        ).then { _ ->

        }
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
                <Foo onClick={} />
            """
        ).then { _ ->

        }
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
                <Foo onClick={} />
            """
        ).then { _ ->

        }
    }

    @Test
    fun testCGNInlining(): Unit = ensureSetup {
        compose(
            """
                <LinearLayout orientation=LinearLayout.VERTICAL>
                    <TextView text="Hello, world!" id=42 />
                </LinearLayout>
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGUpdatedComposition(): Unit = ensureSetup {
        var value = "Hello, world!"

        compose({ mapOf("value" to value) }, """
           <TextView text=value id=42 />
        """).then { activity ->
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

        compose({ mapOf("value" to value) }, """
           <TextView text=value id=42 />
        """).then { activity ->
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

        compose({ mapOf("text" to text, "orientation" to orientation) }, """
            <LinearLayout orientation id=$llId>
              <TextView text id=$tvId />
            </LinearLayout>
        """).then { activity ->
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
    fun testCGNAmbient(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """

            val StringAmbient = Ambient.of<String> { "default" }

            @Composable fun Foo() {
                <StringAmbient.Consumer> value ->
                    <TextView id=$tvId text=value />
                </StringAmbient.Consumer>
            }

        """,
            { mapOf("text" to text) },
            """
            <StringAmbient.Provider value=text>
                <Foo />
            </StringAmbient.Provider>
        """).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    @Test
    fun testCGNClassComponent(): Unit = ensureSetup {
        var text = "Hello, world!"
        val tvId = 123

        compose(
        """
            class Foo {
                var text = ""
                @Composable
                operator fun invoke(bar: Int) {
                    <TextView id=$tvId text=text />
                }
            }

        """,
        { mapOf("text" to text) },
        """
             <Foo text bar=123 />
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
                <TextView id=$tvId text=text />
            }

        """,
            { mapOf("text" to text) },
            """
             <Foo text />
        """,
            true
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
    fun testCGNViewGroup(): Unit = ensureSetup {
        val tvId = 258
        val llId = 260
        var text = "Hello, world!"
        var orientation = LinearLayout.HORIZONTAL

        compose({ mapOf("text" to text, "orientation" to orientation) }, """
             <LinearLayout orientation id=$llId>
               <TextView text id=$tvId />
             </LinearLayout>
        """).then { activity ->
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
    fun testCGNSimpleCall(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """
                @Composable fun SomeFun(x: String) {
                    <TextView text=x id=$tvId />
                }
            """,
            { mapOf("text" to text) },
            """
                <SomeFun x=text />
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

    @Test
    fun testCGNSimpleCall2(): Unit = ensureSetup {
            val tvId = 258
            var text = "Hello, world!"
            var someInt = 456

            compose(
                """
                class SomeClass(var x: String) {
                    @Composable
                    operator fun invoke(y: Int) {
                        <TextView text="${"$"}x ${"$"}y" id=$tvId />
                    }
                }
            """,
                { mapOf("text" to text, "someInt" to someInt) },
                """
                <SomeClass x=text y=someInt />
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
    fun testCGNSimpleCall3(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"
        var someInt = 456

        compose(
            """
                @Stateful
                class SomeClassoawid(var x: String) {
                    @Composable
                    operator fun invoke(y: Int) {
                        <TextView text="${"$"}x ${"$"}y" id=$tvId />
                    }
                }
            """,
            { mapOf("text" to text, "someInt" to someInt) },
            """
                <SomeClassoawid x=text y=someInt />
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
                fun Block(@Children children: () -> Unit) {
                    <children />
                }
            """,
            { mapOf("text" to text) },
            """
                <Block>
                    <Block>
                        <TextView text id=$tvId />
                    </Block>
                </Block>
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

    @Test
    fun testCGNStuff(): Unit = ensureSetup {
        val tvId = 258
        var num = 123

        compose(
            """
                class OneArg {
                    var foo = 0
                    @Composable
                    operator fun invoke() {
                        <TextView text="${"$"}foo" id=$tvId />
                    }
                }
                fun OneArg.setBar(bar: Int) { foo = bar }
            """,
            { mapOf("num" to num) },
            """
            <OneArg bar=num />
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

    @Test
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
                            <TextView text=foo id=$tvId />
                        }
                    }
                }
            """,
            { mapOf("text" to text) },
            """
                val a = A()
                a.foo = text
                <a.B />
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
        compose("""
           fun Phone(value: String) {
             <TextView text=value id=$tvId />
           }
        """, { mapOf("phone" to phone)}, """
           <Phone value=phone />
        """).then { activity ->
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
        compose("""
           var addCalled = 0

           fun AddView(left: Int, right: Int) {
             addCalled++
             <TextView text="${'$'}left + ${'$'}right = ${'$'}{left + right}" id=$tvId />
             <TextView text="${'$'}addCalled" id=$rsId />
           }
        """, { mapOf("left" to left, "right" to right)}, """
           <AddView left right />
        """).then { activity ->
            // Should be called on the first compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals("$left + $right = ${left + right}", (activity.findViewById(tvId) as TextView).text)
        }.then { activity ->
            // Should be skipped on the second compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals("$left + $right = ${left + right}", (activity.findViewById(tvId) as TextView).text)

            left = 1
        }.then { activity ->
            // Should be called again because left changed.
            assertEquals("2", (activity.findViewById(rsId) as TextView).text)
            assertEquals("$left + $right = ${left + right}", (activity.findViewById(tvId) as TextView).text)

            right = 41
        }.then { activity ->
            // Should be called again because right changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
            assertEquals("$left + $right = ${left + right}", (activity.findViewById(tvId) as TextView).text)
        }.then { activity ->
            // Should be skipped because nothing changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
        }
    }

    @Test
    fun testImplicitReceiverPassing1(): Unit = ensureSetup {
        compose(
            """
                fun Int.Foo(x: @Composable() Int.() -> Unit) {
                    <x />
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                <id.Foo x={
                    <TextView text="Hello, world!" id=this />
                } />
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
                fun Int.Foo(x: @Composable() Int.(text: String) -> Unit, text: String) {
                    <x text />
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                <id.Foo text="Hello, world!" x={ text ->
                    <TextView text id=this />
                } />
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
                import com.google.r4a.adapters.*

                @Composable
                fun Counter() {
                    <Observe>
                        var count = +state { 0 }
                        <TextView
                            text=("Count: " + count.value)
                            onClick={
                                count.value += 1
                            }
                            id=42
                        />
                    </Observe>
                }
            """,
            { mapOf<String, String>() },
            """
                <Counter />
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
                import com.google.r4a.adapters.*

                @Model class MyState<T>(var value: T)

                @Composable
                fun Counter() {
                    <Observe>
                        var count = +memo { MyState(0) }
                        <TextView
                            text=("Count: " + count.value)
                            onClick={
                                count.value += 1
                            }
                            id=42
                        />
                    </Observe>
                }
            """,
            { mapOf<String, String>() },
            """
                <Counter />
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
                import com.google.r4a.adapters.*

                @Composable
                fun Counter(log: StringBuilder) {
                    <Observe>
                        var count = +state { 0 }
                        +onCommit {
                            log.append("a")
                        }
                        +onActive {
                            log.append("b")
                        }
                        <TextView
                            text=("Count: " + count.value)
                            onClick={
                                count.value += 1
                            }
                            id=42
                        />
                    </Observe>
                }
            """,
            { mapOf("log" to log) },
            """
                <Counter log />
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            assertEquals("ab", log.toString())
            textView.performClick()
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 1", textView.text)
            assertEquals("aba", log.toString())
        }
    }

    // b/118610495
    @Test
    fun testCGChildCompose(): Unit = ensureSetup {
        val tvId = 153

        var text = "Test 1"

        compose("""
            var called = 0

            class TestContainer(@Children var children: @Composable() ()->Unit): Component() {
              override fun compose() {
                <LinearLayout>
                  <children />
                </LinearLayout>
              }
            }

            class TestClass(var text: String): Component() {
              override fun compose() {
                <TestContainer>
                  <TextView text id=$tvId />
                </TestContainer>
              }
            }
        """, { mapOf("text" to text) }, """
            <TestClass text />
        """).then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals(text, tv.text)

            text = "Test 2"
        }.then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals(text, tv.text)
        }
    }

    @Test
    fun testVariableCalls1(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    <TextView text="Hello, world!" id=42 />
                }
            """,
            { mapOf<String, String>() },
            """
                <component />
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
                    <TextView text="Hello, world!" id=42 />
                }
                class HolderA(val composable: @Composable() () -> Unit)

                val holder = HolderA(component)

            """,
            { mapOf<String, String>() },
            """
                <holder.composable />
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
                    <TextView text="Hello, world!" id=42 />
                }
                class HolderB(val composable: @Composable() () -> Unit) {
                    @Composable
                    fun Foo() {
                        <composable />
                    }
                }

                val holder = HolderB(component)

            """,
            { mapOf<String, String>() },
            """
                <holder.Foo />
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testVariableCalls4(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    <TextView text="Hello, world!" id=42 />
                }
                class HolderC(val composable: @Composable() () -> Unit) {
                    inner class Foo(): Component() {
                        override fun compose() {
                            <composable />
                        }
                    }
                }

                val holder = HolderC(component)

            """,
            { mapOf<String, String>() },
            """
                <holder.Foo />
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
                    <TextView text=b id=a />
                }
            """,
            { mapOf<String, String>() },
            """
                <Foo b="Hello, world!" />
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
                fun Foo(a: Int = 42, b: String, @Children c: () -> Unit) {
                    <c />
                    <TextView text=b id=a />
                }
            """,
            { mapOf<String, String>() },
            """
                <Foo b="Hello, world!">
                </Foo>
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testPropertiesAndCtorParamsOnEmittables(): Unit = codegen(
        """
            class SimpleEmittable(label: String? = null) : Emittable {
                var label: String? = null
                override fun emitInsertAt(index: Int, instance: Emittable) {}
                override fun emitMove(from: Int, to: Int, count: Int) {}
                override fun emitRemoveAt(index: Int, count: Int) {}
            }

            @Composable
            fun foo() {
                <SimpleEmittable label="Foo" />
            }
        """
    )

    override fun setUp() {
        isSetup = true
        super.setUp()
        KtxTypeResolutionExtension.registerExtension(myEnvironment.project, R4aKtxTypeResolutionExtension())
        KtxControlFlowExtension.registerExtension(myEnvironment.project, R4aKtxControlFlowExtension())
        StorageComponentContainerContributor.registerExtension(myEnvironment.project, ComposableAnnotationChecker())
        TypeResolutionInterceptorExtension.registerExtension(myEnvironment.project, R4aTypeResolutionInterceptorExtension())
        SyntheticIrExtension.registerExtension(myEnvironment.project, R4ASyntheticIrExtension())
        KtxParsingExtension.registerExtension(myEnvironment.project, R4aKtxParsingExtension())
        AnalysisHandlerExtension.registerExtension(myEnvironment.project, PackageAnalysisHandlerExtension())
        SyntheticIrExtension.registerExtension(myEnvironment.project, FrameTransformExtension())
        StorageComponentContainerContributor.registerExtension(myEnvironment.project, FrameModelChecker())
//        SyntheticResolveExtension.registerExtension(myEnvironment.project, StaticWrapperCreatorFunctionResolveExtension())
//        SyntheticResolveExtension.registerExtension(myEnvironment.project, WrapperViewSettersGettersResolveExtension())
    }

    private var isSetup = false
    private inline fun <T> ensureSetup(block: () -> T): T {
        if (!isSetup) setUp()
        return block()
    }

    fun codegen(text: String, dumpClasses: Boolean = false): Unit = ensureSetup {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        classLoader("""
           import android.content.Context
           import android.widget.*
           import com.google.r4a.*

           $text

        """, fileName, dumpClasses)
    }

    fun compose(text: String, dumpClasses: Boolean = false): CompositionTest = compose({mapOf<String, Any>()}, text, dumpClasses)

    fun <T: Any> compose(valuesFactory: () -> Map<String, T>, text: String, dumpClasses: Boolean = false) = compose("", valuesFactory, text, dumpClasses)
    fun <T: Any> compose(prefix: String, valuesFactory: () -> Map<String, T>, text: String, dumpClasses: Boolean = false): CompositionTest {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        val candidateValues = valuesFactory()

        @Suppress("NO_REFLECTION_IN_CLASS_PATH")
        val parameterList = candidateValues.map { "${it.key}: ${it.value::class.qualifiedName}" }.joinToString()
        val parameterTypes = candidateValues.map { it.value::class.javaPrimitiveType ?: it.value::class.javaObjectType }.toTypedArray()

        val compiledClasses = classLoader("""
           import android.content.Context
           import android.widget.*
           import com.google.r4a.*

           $prefix

           class $className {

             fun test($parameterList) {
               $text
             }
           }
        """, fileName, dumpClasses)

        val allClassFiles = compiledClasses.allGeneratedFiles.filter { it.relativePath.endsWith(".class") }

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
        val testMethod = instanceClass.getMethod("test", *parameterTypes)

        return compose {
            val values = valuesFactory()
            val arguments = values.map { it.value as Any }.toTypedArray()
            testMethod.invoke(instanceOfClass, *arguments)
        }
    }
}

var uniqueNumber = 0

fun loadClass(loader: ClassLoader, name: String?, bytes: ByteArray): Class<*> {
    val defineClassMethod = ClassLoader::class.javaObjectType.getDeclaredMethod(
        "defineClass",
        String::class.javaObjectType,
        ByteArray::class.javaObjectType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType)
    defineClassMethod.isAccessible = true
    return defineClassMethod.invoke(loader, name, bytes, 0, bytes.size) as Class<*>
}

const val ROOT_ID = 18284847

private class TestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply { id = ROOT_ID })
    }
}

private val Activity.root get() = findViewById(ROOT_ID) as ViewGroup

private class Root(val composable: () -> Unit) : Component() {
    override fun compose() = composable()
}

class CompositionTest(val composable: () -> Unit) {

    inner class ActiveTest(val activity: Activity, val cc: CompositionContext, val component: Component) {

        fun then(block: (activity: Activity) -> Unit): ActiveTest {
            val previous = CompositionContext.current
            CompositionContext.current = cc
            val composer = composer.composer
            val scheduler = RuntimeEnvironment.getMasterScheduler()
            scheduler.pause()
            try {
                composer.startRoot()
                composable()
                composer.endRoot()
                composer.applyChanges()
            } finally {
                CompositionContext.current = previous
            }
            cc.scheduleRecompose()
            scheduler.advanceToLastPostedRunnable()
            block(activity)
            return this
        }
    }

    fun then(block: (activity: Activity) -> Unit): ActiveTest {
        val controller = Robolectric.buildActivity(TestActivity::class.java)
        val activity = controller.create().get()
        val root = activity.root
        val component = Root(composable)
        val cc = CompositionContext.create(root.context, root, component, null)
        cc.context = activity
        return ActiveTest(activity, cc, component).then(block)
    }
}

fun compose(composable: () -> Unit) = CompositionTest(composable)