package androidx.compose.plugins.kotlin.frames

import org.jetbrains.kotlin.psi.KtFile
import androidx.compose.plugins.kotlin.AbstractCodegenTest
import org.junit.Before

class FrameTransformExtensionTests : AbstractCodegenTest() {

    @Before
    fun before() {
        setUp()
    }

    fun testTestUtilities() = testFile("""
        class Foo {
          val s = "This is a test"
        }

        class Test {
          fun test() {
            Foo().s.expectEqual("This is a test")
          }
        }
    """)

    fun testModel_Simple() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel { }

        class Test {
          fun test() {
            frame { MyModel() }
          }
        }
    """)

    fun testModel_OneField() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          var value: String = "default"
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            frame {
              instance.value.expectEqual("default")
              instance.value = "new value"
              instance.value.expectEqual("new value")
            }
            frame {
              instance.value.expectEqual("new value")
            }
          }
        }
    """)

    fun testModel_OneField_Isolation() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          var value: String = "default"
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            val frame1 = suspended {
              instance.value = "new value"
            }
            frame {
              instance.value.expectEqual("default")
            }
            restored(frame1) {
              instance.value.expectEqual("new value")
            }
            frame {
              instance.value.expectEqual("new value")
            }
          }
        }
    """)

    fun testModel_ThreeFields() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          var strVal = "default"
          var intVal = 1
          var doubleVal = 27.2
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            frame {
              instance.strVal.expectEqual("default")
              instance.intVal.expectEqual(1)
              instance.doubleVal.expectEqual(27.2)
            }
            frame {
              instance.strVal = "new value"
            }
            frame {
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(1)
              instance.doubleVal.expectEqual(27.2)
            }
            frame {
              instance.intVal = 2
            }
            frame {
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(2)
              instance.doubleVal.expectEqual(27.2)
            }
          }
        }
    """)

    fun testModel_ThreeFields_Isolation() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          var strVal = "default"
          var intVal = 1
          var doubleVal = 27.2
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            frame {
              instance.strVal.expectEqual("default")
              instance.intVal.expectEqual(1)
              instance.doubleVal.expectEqual(27.2)
            }
            val frame1 = suspended {
              instance.strVal = "new value"
            }
            frame {
              instance.strVal.expectEqual("default")
              instance.intVal.expectEqual(1)
              instance.doubleVal.expectEqual(27.2)
            }
            restored(frame1) {
              instance.intVal = 2
            }
            frame {
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(2)
              instance.doubleVal.expectEqual(27.2)
            }
          }
        }
    """)

    fun testModel_CustomSetter_Isolation() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          var intVal = 0; set(value) { field = value; intVal2 = value + 10 }
          var intVal2 = 10
          var intVal3 = 0; get() = field + 7; set(value) { field = value - 7 }
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            frame {
              instance.intVal.expectEqual(0)
              instance.intVal2.expectEqual(10)
              instance.intVal3.expectEqual(7)

              instance.intVal = 22
              instance.intVal3 = 14

              instance.intVal.expectEqual(22)
              instance.intVal2.expectEqual(32)
              instance.intVal3.expectEqual(14)
            }
            val frame1 = suspended {
              instance.intVal = 32
              instance.intVal3 = 21

              instance.intVal.expectEqual(32)
              instance.intVal2.expectEqual(42)
              instance.intVal3.expectEqual(21)
            }
            frame {
              instance.intVal.expectEqual(22)
              instance.intVal2.expectEqual(32)
              instance.intVal3.expectEqual(14)
            }
            restored(frame1) {
              instance.intVal.expectEqual(32)
              instance.intVal2.expectEqual(42)
              instance.intVal3.expectEqual(21)
            }
            frame {
              instance.intVal.expectEqual(32)
              instance.intVal2.expectEqual(42)
              instance.intVal3.expectEqual(21)
            }
          }
        }
    """)

    fun testModel_PrivateFields_Isolation() = testFile("""
        import androidx.compose.Model

        @Model
        class MyModel {
          private var myIntVal = 1
          private var myStrVal = "default"

          var intVal get() = myIntVal; set(value) { myIntVal = value }
          var strVal get() = myStrVal; set(value) { myStrVal = value }
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }
            frame {
              instance.strVal.expectEqual("default")
              instance.intVal.expectEqual(1)
            }
            val frame1 = suspended {
              instance.strVal = "new value"
              instance.intVal = 2
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(2)
            }
            frame {
              instance.strVal.expectEqual("default")
              instance.intVal.expectEqual(1)
            }
            restored(frame1) {
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(2)
            }
            frame {
              instance.strVal.expectEqual("new value")
              instance.intVal.expectEqual(2)
            }
          }
        }
    """)

    // regression: 144668818
    fun testModel_InitSection() = testFile("""
        import androidx.compose.Model

        @Model
        class Ints {
            var values: IntArray

            init {
                values = intArrayOf(1, 2, 3)
            }
        }

        class Test {
          fun test() {
            val instance = frame { Ints() }
            frame {
              instance.values.size.expectEqual(3)
              instance.values[0].expectEqual(1)
              instance.values[1].expectEqual(2)
              instance.values[2].expectEqual(3)
            }
            frame {
              instance.values = intArrayOf(1, 2, 3, 4)
            }
            frame {
              instance.values.size.expectEqual(4)
              instance.values[0].expectEqual(1)
              instance.values[1].expectEqual(2)
              instance.values[2].expectEqual(3)
              instance.values[3].expectEqual(4)
            }
          }
        }
    """)

    // regression: 144668818
    fun testModel_Initializers() = testFile("""
        import androidx.compose.Model

        @Model
        class ABC(var a: Int, b: Int = a) { val c = a }

        class Test {
          fun test() {
            val instance = frame { ABC(1) }
            frame {
              instance.a.expectEqual(1)
              instance.c.expectEqual(1)
            }
          }
        }
    """)

    fun testModel_NestedClass() = testFile("""
        import androidx.compose.Model

        class Test {
          @Model
          class MyModel {
            var value: String = "default"
          }

          fun test() {
            val instance = frame { MyModel() }
            val frame1 = suspended {
              instance.value = "new value"
            }
            frame {
              instance.value.expectEqual("default")
            }
            restored(frame1) {
              instance.value.expectEqual("new value")
            }
            frame {
              instance.value.expectEqual("new value")
            }
          }
        }
    """)

    fun testModel_NonModelInheritance() = testFile("""
        import androidx.compose.Model

        open class NonModel(var nonTransactedValue: String = "default")

        @Model
        class MyModel : NonModel() {
          var value: String = "default"
        }

        class Test {
          fun test() {
            val instance = frame { MyModel() }

            frame {
              // Ensure the fields have their default values
              instance.nonTransactedValue.expectEqual("default")
              instance.value.expectEqual("default")
            }

            val frame1 = suspended {
              instance.nonTransactedValue = "modified in suspended transaction"
              instance.value = "modified in suspended transaction"
            }

            frame {
              // Transacted field should still be the default value.
              instance.value.expectEqual("default")

              // Inherited non-transacted field is not isolated and is expected to be modified
              instance.nonTransactedValue.expectEqual("modified in suspended transaction")
            }

            restored(frame1) {
              // Now both fields should appear modified.
              instance.value.expectEqual("modified in suspended transaction")
              instance.nonTransactedValue.expectEqual("modified in suspended transaction")
            }

            // Non-transacted values should be accessible outside a frame
            instance.nonTransactedValue.expectEqual("modified in suspended transaction")

            frame {
              // New frames should see both fields as modified.
              instance.value.expectEqual("modified in suspended transaction")
              instance.nonTransactedValue.expectEqual("modified in suspended transaction")
            }
          }
        }
    """)

    override fun helperFiles(): List<KtFile> = listOf(sourceFile("Helpers.kt",
        HELPERS
    ))
}

const val HELPERS = """
    import androidx.compose.frames.open
    import androidx.compose.frames.commit
    import androidx.compose.frames.suspend
    import androidx.compose.frames.restore
    import androidx.compose.frames.Frame

    inline fun <T> frame(crossinline block: ()->T): T {
        open(false)
        try {
          return block()
        } finally {
          commit()
        }
    }

    inline fun suspended(crossinline block: ()->Unit): Frame {
      open(false)
      block()
      return suspend()
    }

    inline fun restored(frame: Frame, crossinline block: ()->Unit) {
      restore(frame)
      block()
      commit()
    }

    inline fun continued(frame: Frame, crossinline block: ()->Unit): Frame {
      restore(frame)
      block()
      return suspend()
    }

    fun Any.expectEqual(expected: Any) {
      expect(expected, this)
    }

    fun expect(expected: Any, received: Any) {
      if (expected != received) {
        throw Exception("Expected \"${'$'}expected\" but received \"${'$'}received\"")
      }
    }"""
