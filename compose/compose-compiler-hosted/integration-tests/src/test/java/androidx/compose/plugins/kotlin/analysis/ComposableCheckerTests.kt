package androidx.compose.plugins.kotlin.analysis

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import androidx.compose.plugins.kotlin.AbstractComposeDiagnosticsTest
import androidx.compose.plugins.kotlin.newConfiguration
import com.intellij.openapi.util.Disposer

class ComposableCheckerTests : AbstractComposeDiagnosticsTest() {

    companion object {
        val MODE_KTX_CHECKED = 1
        val MODE_KTX_STRICT = 2
        val MODE_KTX_PEDANTIC = 4
        val MODE_FCS = 8
    }

    override fun setUp() {
        // intentionally don't call super.setUp() here since we are recreating an environment
        // every test
        System.setProperty("user.dir",
            homeDir
        )
    }

    fun doTest(text: String, expectPass: Boolean) {
        val disposable = TestDisposable()
        val classPath = createClasspath()
        val configuration = newConfiguration()
        configuration.addJvmClasspathRoots(classPath)

        val environment =
            KotlinCoreEnvironment.createForTests(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
        setupEnvironment(environment)

        try {
            doTest(text, environment)
            if (!expectPass) {
                throw Exception(
                    "Test unexpectedly passed, but SHOULD FAIL"
                )
            }
        } catch (e: Exception) {
            if (expectPass) throw Exception(e)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun check(expectedText: String) {
        doTest(expectedText, true)
    }

    fun checkFail(expectedText: String) {
        doTest(expectedText, false)
    }

    fun testComposableReporting001() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun myStatelessFunctionalComponent() {
                <TextView text="Hello World!" />
            }

            fun foo() {
                <myStatelessFunctionalComponent />
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun myStatelessFunctionalComponent() {
                <TextView text="Hello World!" />
            }

            @Composable
            fun foo() {
                <myStatelessFunctionalComponent />
            }
        """)
    }

    fun testComposableReporting002() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            val myLambda1 = { <TextView text="Hello World!" /> }
            val myLambda2: ()->Unit = { <TextView text="Hello World!" /> }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            val myLambda1 = @Composable() { <TextView text="Hello World!" /> }
            val myLambda2: @Composable() ()->Unit = { <TextView text="Hello World!" /> }
        """)
    }

    fun testComposableReporting003() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun <!KTX_IN_NON_COMPOSABLE!>myRandomFunction<!>() {
                <TextView text="Hello World!" />
            }
        """)
    }

    fun testComposableReporting004() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                val myRandomLambda = <!KTX_IN_NON_COMPOSABLE!>{ <TextView text="Hello World!" /> }<!>
                System.out.println(myRandomLambda)
            }
        """)
    }

    fun testComposableReporting005() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                override fun compose() {
                    <TextView text="Hello World!" />
                }
            }
        """)
    }

    fun testComposableReporting006() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val bar = {
                    <TextView />
                }
                <bar />
                System.out.println(bar)
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                val bar = @Composable {
                    <TextView />
                }
                <bar />
                System.out.println(bar)
            }
        """)
    }

    fun testComposableReporting007() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo(children: @Composable() ()->Unit) {
                <!SVC_INVOCATION!>children<!>()
            }
        """)
    }

    fun testComposableReporting008() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val bar: @Composable() ()->Unit = @Composable {
                    <TextView />
                }
                <!SVC_INVOCATION!>bar<!>()
                System.out.println(bar)
            }
        """)
    }

    fun testComposableReporting009() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun myStatelessFunctionalComponent() {
                <TextView text="Hello World!" />
            }

            fun noise() {
                <!SVC_INVOCATION!>myStatelessFunctionalComponent<!>()
            }
        """)
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun myStatelessFunctionalComponent() {
                <TextView text="Hello World!" />
            }

            fun noise() {
                <!SVC_INVOCATION!>myStatelessFunctionalComponent<!>()
            }
        """)
    }

    fun testComposableReporting010() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                @Children lateinit var children: @Composable() ()->Unit
                override fun compose() {
                    val children = this.children
                    <children />
                    System.out.println(children)
                }
            }
        """)
    }

    fun testComposableReporting011() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                @Children lateinit var children: @Composable() ()->Unit
                override fun compose() {
                    <!SVC_INVOCATION!>children<!>()
                }
            }
        """)
    }

    fun testComposableReporting012() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                @Children lateinit var children: ()->Unit
                override fun compose() {
                    <!SVC_INVOCATION!>children<!>()
                }
            }
        """)
    }

    fun testComposableReporting013() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                @Children(composable=false) lateinit var children: (value: Int)->Unit
                override fun compose() {
                    children(5)
                }
            }
        """)
    }

    fun testComposableReporting014() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyReceiver {}

            class MyComponent : Component() {
                @Children(composable=false) lateinit var children: MyReceiver.()->Unit
                override fun compose() {
                    MyReceiver().children()
                }
            }
        """)
    }

    fun testComposableReporting015() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                override fun compose() {
                    <helper1 />
                    <helper2 />
                }

                fun helper1() {
                    <TextView text="Hello Helper" />
                }

                @Composable
                fun helper2() {
                    <TextView text="Hello Helper" />
                }
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                override fun compose() {
                    <helper1 />
                    <helper2 />
                }

                fun <!KTX_IN_NON_COMPOSABLE!>helper1<!>() {
                    <TextView text="Hello Helper" />
                }

                @Composable
                fun helper2() {
                    <TextView text="Hello Helper" />
                }
            }
        """)
    }

    fun testComposableReporting016() {
        check("""
            import androidx.compose.*;

            <!WRONG_ANNOTATION_TARGET!>@Composable<!>
            class Noise() {}
        """)

        check("""
            import androidx.compose.*;

            val adHoc = <!WRONG_ANNOTATION_TARGET!>@Composable()<!> object {
                var x: Int = 0
                var y: Int = 0
            }
        """)

        check("""
            import androidx.compose.*;

            open class Noise() {}

            val adHoc = <!WRONG_ANNOTATION_TARGET!>@Composable()<!> object : Noise() {
                var x: Int = 0
                var y: Int = 0
            }
        """)
    }

    fun testComposableReporting017() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.LinearLayout;
            import android.widget.TextView;

            @Composable
            fun Foo(@Children(composable=false) children: ()->Unit) {
                children()
            }

            @Composable
            fun main() {
                <Foo><TextView text="Hello" /></Foo>
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.LinearLayout;
            import android.widget.TextView;

            @Composable
            fun Foo(@Children(composable=false) children: ()->Unit) {
                children()
            }

            @Composable
            fun main() {
                <Foo><!KTX_IN_NON_COMPOSABLE!><TextView text="Hello" /><!></Foo>
            }
        """)
    }

    fun testComposableReporting018() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val myVariable: ()->Unit = @Composable { <TextView text="Hello World!" /> }
                System.out.println(myVariable)
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val myVariable: ()->Unit = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>@Composable { <TextView text="Hello World!" /> }<!>
                System.out.println(myVariable)
            }
        """)
    }

    fun testComposableReporting019() {
        checkFail("""
           import androidx.compose.*;
           import android.widget.TextView;

           @Composable
           fun foo() {
               val myVariable: ()->Unit = { }
               <<!NON_COMPOSABLE_INVOCATION!>myVariable<!> />
               System.out.println(myVariable)
           }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                val nonComposableLambda: ()->Unit = { }
                <<!NON_COMPOSABLE_INVOCATION!>nonComposableLambda<!> />
                System.out.println(nonComposableLambda)
            }
        """)
    }

    fun testComposableReporting020() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun nonComposableFunction() {}

            @Composable
            fun foo() {
                <<!NON_COMPOSABLE_INVOCATION!>nonComposableFunction<!> />
            }
        """)
    }

    fun testComposableReporting021() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                val myList = listOf(1,2,3,4,5)
                myList.forEach @Composable { value: Int -> <TextView text=value.toString() />; System.out.println(value); }
            }
        """)
    }

    fun testComposableReporting022() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val myList = listOf(1,2,3,4,5)
                myList.forEach { value: Int -> <TextView text=value.toString() />; System.out.println(value); }
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun <!KTX_IN_NON_COMPOSABLE!>foo<!>() {
                val myList = listOf(1,2,3,4,5)
                myList.forEach @Composable { value: Int -> <TextView text=value.toString() />; System.out.println(value); }
            }
        """)
    }

    fun testComposableReporting023() {
        check("""
               import androidx.compose.*;
               import android.widget.TextView;

               fun foo() {}

               @Composable
               fun bar() {
                    <<!NON_COMPOSABLE_INVOCATION!>foo<!> />
               }
           """)
    }

    fun testComposableReporting024() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;
            import android.widget.LinearLayout;

            fun foo(ll: LinearLayout) {
                ll.setViewContent({ <TextView text="Hello World!" /> })
            }
        """)
    }

    fun testComposableReporting025() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                listOf(1,2,3,4,5).forEach { <TextView text="Hello World!" /> }
            }
        """)
    }

    fun testComposableReporting026() {
        check("""
            import androidx.compose.*;
            import android.widget.LinearLayout;
            import android.widget.TextView;

            @Composable
            fun foo() {
                <LinearLayout>
                    <TextView text="Hello Jim!" />
                </LinearLayout>
            }
        """)
    }

    fun testComposableReporting027() {
        check("""
            import androidx.compose.*;
            import android.widget.LinearLayout;
            import android.widget.TextView;

            @Composable
            fun foo() {
                <LinearLayout>
                    listOf(1,2,3).forEach {
                        <TextView text="Hello Jim!" />
                    }
                </LinearLayout>
            }
        """)
    }

    fun testComposableReporting028() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo(v: @Composable() ()->Unit) {
                val myVariable: ()->Unit = v
                myVariable()
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo(v: @Composable() ()->Unit) {
                val myVariable: ()->Unit = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>v<!>
                myVariable()
            }
        """)
    }

    fun testComposableReporting029() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo(v: ()->Unit) {
                val myVariable: @Composable() ()->Unit = v;
                <myVariable />
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo(v: ()->Unit) {
                val myVariable: @Composable() ()->Unit = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>v<!>;
                <myVariable />
            }
        """)
    }

    fun testComposableReporting030() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            @Composable
            fun foo() {
                val myVariable: @Composable() ()->Unit = {};
                <myVariable />
            }
        """)
    }

    fun testComposableReporting031() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun foo() {
                val myVariable: ()->Unit = <!KTX_IN_NON_COMPOSABLE!>{ <TextView text="Hello" /> }<!>;
                myVariable();
            }
        """)
    }

    fun testComposableReporting032() {
        check("""
            import androidx.compose.*;
            import androidx.compose.Children;
            import android.widget.TextView;

            @Composable
            fun MyComposable(@Children children: ()->Unit) { <children /> }

            @Composable
            fun foo() {
                <MyComposable><TextView text="Hello" /></MyComposable>
            }
        """)
    }

    fun testComposableReporting033() {
        check("""
            import androidx.compose.*;
            import androidx.compose.Children;
            import android.widget.TextView;

            @Composable
            fun MyComposable(@Children children: ()->Unit) { <children /> }

            @Composable
            fun foo() {
                <MyComposable children={<TextView text="Hello" />} />
            }
        """)
    }

    fun testComposableReporting034() {
        checkFail("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun identity(f: ()->Unit): ()->Unit { return f; }

            @Composable
            fun test(f: @Composable() ()->Unit) {
                val f2: @Composable() ()->Unit = identity(f);
                <f2 />
            }
        """)
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            fun identity(f: ()->Unit): ()->Unit { return f; }

            @Composable
            fun test(f: @Composable() ()->Unit) {
                val f2: @Composable() ()->Unit = <!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>identity(<!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>f<!>)<!>;
                <f2 />
            }
        """)
    }

    fun testComposableReporting035() {
        check("""
            import androidx.compose.*

            @Composable
            fun Foo(x: String) {
                @Composable operator fun String.invoke() {}
                <x />
            }
        """)
    }

    fun testComposableReporting036() {
        checkFail("""
            import androidx.compose.*
            import android.widget.TextView;

            fun Foo() {
                repeat(5) {
                    <TextView text="Hello World" />
                }
            }

            fun Bar() {
                <!SVC_INVOCATION!>Foo<!>()
            }
        """)
        checkFail("""
            import androidx.compose.*
            import android.widget.TextView;

            fun Foo() {
                repeat(5) {
                    <TextView text="Hello World" />
                }
            }

            fun Bar() {
                <!SVC_INVOCATION!>Foo<!>()
            }
        """)
    }

    fun testComposableReporting037() {
        checkFail("""
            import androidx.compose.*
            import android.widget.TextView;

            fun Foo() {
                fun Noise() {
                    <TextView text="Hello World" />
                }
            }

            fun Bar() {
                Foo()
            }
        """)
    }

    fun testComposableReporting038() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            // Function intentionally not inline
            fun repeat(x: Int, l: ()->Unit) { for(i in 1..x) l() }

            fun Foo() {
                repeat(5) <!KTX_IN_NON_COMPOSABLE!>{
                    <TextView text="Hello World" />
                }<!>
            }
        """)
    }

    fun testComposableReporting039() {
        checkFail("""
            import androidx.compose.*
            import android.widget.TextView;

            fun composeInto(l: @Composable() ()->Unit) { System.out.println(l) }

            fun Foo() {
                composeInto {
                    <TextView text="Hello World" />
                }
            }

            fun Bar() {
                Foo()
            }
        """)
        checkFail("""
            import androidx.compose.*
            import android.widget.TextView;

            inline fun noise(l: ()->Unit) { l() }

            fun Foo() {
                noise {
                    <TextView text="Hello World" />
                }
            }

            fun Bar() {
                <!SVC_INVOCATION!>Foo<!>()
            }
        """)
    }

    fun testComposableReporting040() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            @Composable
            fun ComposeWrapperComposable(children: @Composable() () -> Unit) {
                <MyComposeWrapper>
                    <children />
                </MyComposeWrapper>
            }

            class MyComposeWrapper(var children: @Composable() () -> Unit) : Component() {
                override fun compose() { }
            }
        """)
    }

    fun testComposableReporting041() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            typealias COMPOSABLE_UNIT_LAMBDA = @Composable() () -> Unit

            @Composable
            fun ComposeWrapperComposable(children: COMPOSABLE_UNIT_LAMBDA) {
                <MyComposeWrapper>
                    <children />
                </MyComposeWrapper>
            }

            class MyComposeWrapper(var children: COMPOSABLE_UNIT_LAMBDA) : Component() {
                override fun compose() { }
            }
        """)
    }

    fun testComposableReporting042() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            fun composeInto(l: @Composable() ()->Unit) { System.out.println(l) }

            @Composable
            fun FancyButton() {}

            fun Foo() {
                composeInto {
                    FancyButton()
                }
            }

            fun Bar() {
                Foo()
            }
        """)
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            inline fun noise(l: ()->Unit) { l() }

            @Composable
            fun FancyButton() {}

            fun <!COMPOSABLE_INVOCATION_IN_NON_COMPOSABLE!>Foo<!>() {
                noise {
                    <!COMPOSABLE_INVOCATION_IN_NON_COMPOSABLE!>FancyButton<!>()
                }
            }
        """)
    }

    fun testComposableReporting043() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            typealias UNIT_LAMBDA = () -> Unit

            @Composable
            fun FancyButton() {}

            fun <!COMPOSABLE_INVOCATION_IN_NON_COMPOSABLE!>Noise<!>() {
                <!COMPOSABLE_INVOCATION_IN_NON_COMPOSABLE!>FancyButton<!>()
            }
        """)
    }

    fun testComposableReporting044() {
        check("""
            import androidx.compose.*
            import android.widget.TextView;

            typealias UNIT_LAMBDA = () -> Unit

            @Composable
            fun FancyButton() {}

            @Composable
            fun Noise() {
                FancyButton()
            }
        """)
    }

    fun testComposableReporting045() {
        check("""
            import androidx.compose.*;

            @Composable
            fun foo() {
                val bar = @Composable {}
                bar()
                System.out.println(bar)
            }
        """)
    }

    fun testComposableReporting046() {
        check("""
            import androidx.compose.*;
            import android.widget.TextView;

            class MyComponent : Component() {
                @Children lateinit var children: @Composable() ()->Unit
                override fun compose() {
                    val children = this.children
                    children()
                    System.out.println(children)
                }
            }
        """)
    }
}
