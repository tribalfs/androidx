package org.jetbrains.kotlin.r4a


class KtxCallResolutionTests : AbstractResolvedKtxCallsTest() {

    fun testSomethingQualifiedTag() = doTest(
        """
            import com.google.r4a.*

            object Foo {
                class Bar {
                    @Composable
                    operator fun invoke() {}
                }
            }

            @Composable
            fun test() {
                <caret><Foo.Bar />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations = <empty>
                call = NonMemoizedCallNode:
                  resolvedCall = Bar()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke()
                    params = <empty>
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = <empty>
              unusedAttributes = <empty>
        """
    )

    fun testReceiverScopeTag() = doTest(
        """
            import com.google.r4a.*

            class Foo {}

            @Composable
            fun test(children: Foo.() -> Unit) {
                val foo = Foo()
                <caret><foo.children />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations = <empty>
                call = NonMemoizedCallNode:
                  resolvedCall = fun Foo.invoke()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = <empty>
              unusedAttributes = <empty>
        """
    )

    fun testSomething() = doTest(
        """
            import com.google.r4a.*

            class Bar(var y: Int = 0) {
                @Composable
                operator fun invoke(z: Int) {

                }
            }

            @Composable
            fun test() {
                <caret><Bar y=2 z=3>
                </Bar>
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = var y: Int
                        attribute = y
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = NonMemoizedCallNode:
                  resolvedCall = Bar(Int)
                  params = y
                  postAssignments = <empty>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke(Int)
                    params = z
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = y, z
              unusedAttributes = <children>
        """
    )

    fun testNestedCalls() = doTest(
        """
            import com.google.r4a.*

            @Memoized
            class Bar(var y: Int = 0) {
                @Composable
                operator fun invoke(z: Int) {

                }
            }

            @Memoized
            @Composable
            fun Foo(a: Int): @Composable() (y: Int) -> Bar = { y: Int -> Bar(y) }

            @Composable
            fun test() {
                <caret><Foo a=1 y=2 z=3 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, () -> (Int) -> Bar, noinline ViewValidator.((Int) -> Bar) -> Boolean, ((Int) -> Bar) -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = fun Foo(Int): (Int) -> Bar
                  ctorParams = a
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = y
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = MemoizedCallNode:
                  memoize = ComposerCallInfo:
                    composerCall = fun call(Any, () -> Bar, noinline ViewValidator.(Bar) -> Boolean, (Bar) -> Unit)
                    pivotals = <empty>
                    joinKeyCall = fun joinKey(Any, Any?): Any
                    ctorCall = fun invoke(Int): Bar
                    ctorParams = y
                    validations =
                      - ValidatedAssignment(UPDATE):
                          validationCall = fun update(Int, (Int) -> Unit): Boolean
                          assignment = var y: Int
                          attribute = y
                      - ValidatedAssignment(CHANGED):
                          validationCall = fun changed(Int): Boolean
                          assignment = <null>
                          attribute = z
                  call = NonMemoizedCallNode:
                    resolvedCall = fun invoke(Int)
                    params = z
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = z, y, a
              unusedAttributes = <empty>
        """
    )

    fun testTopLevelFunction() = doTest(
        """
            import com.google.r4a.*

            @Composable
            fun Foo(a: Int, z: Int) {

            }

            @Composable
            fun test() {
                <caret><Foo a=1 z=3 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = a
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = NonMemoizedCallNode:
                  resolvedCall = fun Foo(Int, Int)
                  params = a, z
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = a, z
              unusedAttributes = <empty>
        """
    )

    fun testParameterNamesForInstantiatedClassObjects() = doTest(
        """
            import com.google.r4a.*

            class Bar {
                @Composable
                operator fun invoke(z: Int) {

                }
            }

            @Composable
            fun test() {
                val x = Bar()
                <caret><x z=3 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Bar): Boolean
                        assignment = <null>
                        attribute = <tag>
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke(Int)
                  params = z
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = z, <tag>
              unusedAttributes = <empty>
        """
    )

    fun testParameterNamesForLambdas() = doTest(
        """
            import com.google.r4a.*

            @Composable
            fun test() {
                val x: (z: Int) -> Unit = { z: Int -> }
                <caret><x z=3 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed((Int) -> Unit): Boolean
                        assignment = <null>
                        attribute = <tag>
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke(Int)
                  params = z
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = z, <tag>
              unusedAttributes = <empty>
        """
    )

    fun testSomethingWithChildren() = doTest(
        """
            import com.google.r4a.*

            class Bar(var y: Int = 0) {
                @Children var children: @Composable() () -> Unit = {}
                @Composable
                operator fun invoke(z: Int) {

                }
            }

            @Composable
            fun test() {
                <caret><Bar y=2 z=3>
                </Bar>
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = var y: Int
                        attribute = y
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(() -> Unit): Boolean
                        assignment = <null>
                        attribute = <children>
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = z
                call = NonMemoizedCallNode:
                  resolvedCall = Bar(Int)
                  params = y
                  postAssignments =
                    - ValidatedAssignment(SET):
                        validationCall = fun set(() -> Unit, (() -> Unit) -> Unit): Boolean
                        assignment = var children: () -> Unit
                        attribute = <children>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke(Int)
                    params = z
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = y, <children>, z
              unusedAttributes = <empty>
        """
    )

    fun testViewResolution() = doTest(
        """
            import com.google.r4a.*
            import android.widget.Button

            @Composable
            fun test() {
                <caret><Button text="some text" enabled=false />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = EmitCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun emit(Any, (Context) -> Button, noinline ViewUpdater<Button>.() -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = constructor Button(Context!)
                  ctorParams = (implicit)context
                  validations =
                    - ValidatedAssignment(SET):
                        validationCall = fun set(String, Button.(String) -> Unit)
                        assignment = fun setText(CharSequence!)
                        attribute = text
                    - ValidatedAssignment(SET):
                        validationCall = fun set(Boolean, Button.(Boolean) -> Unit)
                        assignment = fun setEnabled(Boolean)
                        attribute = enabled
              usedAttributes = text, enabled
              unusedAttributes = <empty>
        """
    )

    fun testNonMemoizableClassComponent() = doTest(
        """
            import com.google.r4a.*

            class Bar(var a: Int) {
                @Composable
                operator fun invoke(b: Int) {}
            }

            @Composable
            fun test() {
                <caret><Bar a=1 b=2 />
            }
            /*
                call(k, { changed(a) + changed(b) }) {
                    Bar(a)(b)
                }
            */
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = var a: Int
                        attribute = a
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = b
                call = NonMemoizedCallNode:
                  resolvedCall = Bar(Int)
                  params = a
                  postAssignments = <empty>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke(Int)
                    params = b
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = a, b
              unusedAttributes = <empty>
        """
    )

    fun testChildrenLambdaSetterWithMultipleParams() = doTest(
        """
            import com.google.r4a.*

            class Example {
                @Children
                fun setChildren(fn: (x: Int, y: String) -> Unit) {}
                @Composable
                operator fun invoke() {}
            }

            @Composable
            fun helper(x: Int = 4, y: Int) {
            }

            fun run(text: String) {

//                <helper y=123 />

//                val lambda = { x, y -> ... }
//                composer.call(
//                    123,
//                    { changed(lambda) },
//                    { Example().apply { setChildren(lambda) }() }
//                )

                <caret><Example> x, y ->
                    println("hello ${"$"}x ${"$"}y")
                </Example>
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed((Int, String) -> Unit): Boolean
                        assignment = <null>
                        attribute = <children>
                call = NonMemoizedCallNode:
                  resolvedCall = Example()
                  params = <empty>
                  postAssignments =
                    - ValidatedAssignment(SET):
                        validationCall = fun set((Int, String) -> Unit, ((Int, String) -> Unit) -> Unit): Boolean
                        assignment = fun setChildren((Int, String) -> Unit)
                        attribute = <children>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke()
                    params = <empty>
                    postAssignments = <empty>
                    nextCall = <null>
              usedAttributes = <children>
              unusedAttributes = <empty>
        """
    )

    fun testMemoizableClassComponent() = doTest(
        """
            import com.google.r4a.*

            @Memoized
            class Bar(var a: Int) {
                @Composable
                operator fun invoke(b: Int) {}
            }

            @Composable
            fun test() {
                <caret><Bar a=1 b=2 />
            }
            /*
                call(k, { Bar(a) }, { update(a) { a = it } + changed(b) }) { bar ->
                    bar(b)
                }
            */
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, () -> Bar, noinline ViewValidator.(Bar) -> Boolean, (Bar) -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = Bar(Int)
                  ctorParams = a
                  validations =
                    - ValidatedAssignment(UPDATE):
                        validationCall = fun update(Int, (Int) -> Unit): Boolean
                        assignment = var a: Int
                        attribute = a
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = b
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke(Int)
                  params = b
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = b, a
              unusedAttributes = <empty>
        """
    )

    fun testMemoizableClassComponentComponent() = doTest(
        """
            import com.google.r4a.*

            class Bar(var a: Int): Component() {
                override fun compose() {}
            }

            @Composable
            fun test() {
                <caret><Bar a=1 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, () -> Bar, noinline ViewValidator.(Bar) -> Boolean, (Bar) -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = Bar(Int)
                  ctorParams = a
                  validations =
                    - ValidatedAssignment(UPDATE):
                        validationCall = fun update(Int, (Int) -> Unit): Boolean
                        assignment = var a: Int
                        attribute = a
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = a
              unusedAttributes = <empty>
        """
    )

    fun testMemoizableClassComponentComponent2() = doTest(
        """
            import com.google.r4a.*

            class TestContainer(@Children var children: @Composable() (x: Double)->Unit): Component() {
              override fun compose() {}
            }

            @Composable
            fun test() {
                <caret><TestContainer> x->

                </TestContainer>
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, () -> TestContainer, noinline ViewValidator.(TestContainer) -> Boolean, (TestContainer) -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = TestContainer((Double) -> Unit)
                  ctorParams = <children>
                  validations =
                    - ValidatedAssignment(UPDATE):
                        validationCall = fun update((Double) -> Unit, ((Double) -> Unit) -> Unit): Boolean
                        assignment = var children: (Double) -> Unit
                        attribute = <children>
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = <children>
              unusedAttributes = <empty>
        """
    )

    fun testDynamicTagsMemoizableClassComponent() = doTest(
        """
            import com.google.r4a.*

            @Memoized
            class Bar(var a: Int) {
                @Composable
                operator fun invoke(b: Int) {}
            }

            @Composable
            fun test() {
                val bar = Bar()
                <caret><bar b=2 />
            }
            /*
                call(k, { changed(bar) + changed(b) }) {
                    bar(b)
                }
            */
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Bar): Boolean
                        assignment = <null>
                        attribute = <tag>
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = b
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke(Int)
                  params = b
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = b, <tag>
              unusedAttributes = <empty>
        """
    )

    fun testDynamicTagsInnerClassMemoizableClassComponent() = doTest(
        """
            import com.google.r4a.*


            class Bar(var a: Int) {
                @Memoized
                inner class Foo {
                    @Composable
                    operator fun invoke(b: Int) {}
                }
            }

            @Composable
            fun test() {
                val bar = Bar()
                <caret><bar.Foo b=2 />
            }
            /*
                call(k, { changed(bar) + changed(b) }) {
                    bar.Foo(b)
                }
            */
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, () -> Bar.Foo, noinline ViewValidator.(Bar.Foo) -> Boolean, (Bar.Foo) -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = Foo()
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Bar): Boolean
                        assignment = <null>
                        attribute = <tag>
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = b
                call = NonMemoizedCallNode:
                  resolvedCall = fun invoke(Int)
                  params = b
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = b, <tag>
              unusedAttributes = <empty>
        """
    )

    fun testDeepNestedCalls() = doTest(
        """
            import com.google.r4a.*


            class A { operator fun invoke(a: Int): B { return B() } }
            class B { operator fun invoke(b: Int): C { return C() } }
            class C { operator fun invoke(c: Int): D { return D() } }
            class D { operator fun invoke(d: Int): E { return E() } }
            class E { operator fun invoke(e: Int) {} }

            @Composable
            fun test() {
                <caret><A a=1 b=2 c=3 d=4 e=5 />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = a
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = b
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = c
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = d
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = e
                call = NonMemoizedCallNode:
                  resolvedCall = A()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = NonMemoizedCallNode:
                    resolvedCall = fun invoke(Int): B
                    params = a
                    postAssignments = <empty>
                    nextCall = NonMemoizedCallNode:
                      resolvedCall = fun invoke(Int): C
                      params = b
                      postAssignments = <empty>
                      nextCall = NonMemoizedCallNode:
                        resolvedCall = fun invoke(Int): D
                        params = c
                        postAssignments = <empty>
                        nextCall = NonMemoizedCallNode:
                          resolvedCall = fun invoke(Int): E
                          params = d
                          postAssignments = <empty>
                          nextCall = NonMemoizedCallNode:
                            resolvedCall = fun invoke(Int)
                            params = e
                            postAssignments = <empty>
                            nextCall = <null>
              usedAttributes = a, b, c, d, e
              unusedAttributes = <empty>
        """
    )

    fun testRecursionLimitNoParams() = doTest(
        """
            import com.google.r4a.*


            class A { operator fun invoke(): B { return B() } }
            class B { operator fun invoke(): C { return C() } }
            class C { operator fun invoke(): D { return D() } }
            class D { operator fun invoke(): E { return E() } }
            class E { operator fun invoke() {} }

            @Composable
            fun test() {
                <caret><A />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations = <empty>
                call = NonMemoizedCallNode:
                  resolvedCall = A()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = <ERROR:RecursionLimitError>
              usedAttributes = <empty>
              unusedAttributes = <empty>
        """
    )

    fun testPivotalsAreNotValidated() = doTest(
        """
            import com.google.r4a.*

            @Composable
            fun A(@Pivotal x: Int, y: Int) {
                println(x)
                println(y)
            }

            @Composable
            fun test(x: Int, y: Int) {
                <caret><A x y />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = x
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations =
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = x
                    - ValidatedAssignment(CHANGED):
                        validationCall = fun changed(Int): Boolean
                        assignment = <null>
                        attribute = y
                call = NonMemoizedCallNode:
                  resolvedCall = fun A(Int, Int)
                  params = x, y
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = x, y
              unusedAttributes = <empty>
        """
    )



    fun testIsStaticNature() = doTest(
        """
            import com.google.r4a.*

//            class Bar {
//                @Composable
//                operator fun invoke() {}
//            }

            val Bar get() = @Composable { }

            // All of these should be "static"
            // ===============================

            // class A { operator fun invoke() }
            // <A />

            // class A { companion object { class B { operator fun invoke() {} } } }
            // <A.B />

            // class A { companion object { fun B() {} } }
            // <A.B />

            // class A { class B { operator fun invoke() {} } } }
            // <A.B />

            // object A { fun B() {} }
            // <A.B />

            // object A { object B { fun C() {} } }
            // <A.B.C />

            // object A { class B { operator fun invoke() {} } }
            // <A.B />

            // fun A() {}
            // <A />

            // val A = @Composable {}
            // <A />

            // class A { inner class B { operator fun invoke() {} } }
            // val a = A()
            // <a.B />


            // All of these should be "dynamic"
            // ===============================

            // var A = @Composable {}
            // <A />

            // val A get() = @Composable {}
            // <A />

            // object A { inner class B { operator fun invoke() {} } }
            // <A.B />

            // class A { inner class B { operator fun invoke() {} } }
            // A().let { <it.B /> }


            class UI {
                @Composable
                fun Foo() {}
            }
            val ui = UI()

            @Composable
            fun test() {
                <caret><ui.Foo />
            }
        """,
        """
            ResolvedKtxElementCall:
              emitOrCall = MemoizedCallNode:
                memoize = ComposerCallInfo:
                  composerCall = fun call(Any, noinline ViewValidator.() -> Boolean, () -> Unit)
                  pivotals = <empty>
                  joinKeyCall = fun joinKey(Any, Any?): Any
                  ctorCall = <null>
                  ctorParams = <empty>
                  validations = <empty>
                call = NonMemoizedCallNode:
                  resolvedCall = fun Foo()
                  params = <empty>
                  postAssignments = <empty>
                  nextCall = <null>
              usedAttributes = <empty>
              unusedAttributes = <empty>
        """
    )
}

