package androidx.wear.ongoing

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument
import java.lang.IllegalStateException

@RunWith(PatchedRobolectricTestRunner::class)
@DoNotInstrument
@Config(sdk = [Build.VERSION_CODES.Q])
open class OngoingActivityStatusTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testTextOngoingActivityStatusText() {
        val status = OngoingActivityStatus.Builder()
            .addPart("1", TextStatusPart("First Text"))
            .addPart("2", TextStatusPart("Second Text"))
            .build()

        assertEquals(Long.MAX_VALUE, status.getNextChangeTimeMillis(0))

        assertEquals("First Text Second Text", status.getText(context, 0).toString())
    }

    @Test
    fun testTextOngoingActivityStatusTextAndTemplate() {
        val status = OngoingActivityStatus.Builder()
            .addPart("one", TextStatusPart("First Text"))
            .addPart("two", TextStatusPart("Second Text"))
            .addTemplate("#one# | #two#").build()

        assertEquals(Long.MAX_VALUE, status.getNextChangeTimeMillis(0))

        assertEquals(
            "First Text | Second Text",
            status.getText(context, 0).toString()
        )
    }

    @Test
    fun testTextOngoingActivityStatusTextAndTemplate2() {
        val status = OngoingActivityStatus.Builder()
            .addPart("a", TextStatusPart("A"))
            .addPart("b", TextStatusPart("B"))
            .addTemplate("#a##b#").build()

        assertEquals(Long.MAX_VALUE, status.getNextChangeTimeMillis(0))

        assertEquals(
            "AB",
            status.getText(context, 0).toString()
        )
    }

    @Test
    fun testTextOngoingActivityStatusMixed() {
        val t0 = 123456L
        val status = OngoingActivityStatus.Builder()
            .addPart("type", TextStatusPart("Workout"))
            .addPart("time", TimerStatusPart(t0, /* countDown = */ false))
            .addTemplate("The time on your #type# is #time#")
            .build()

        assertEquals(t0 + 1000, status.getNextChangeTimeMillis(t0))

        assertEquals(
            "The time on your Workout is 01:00",
            status.getText(context, t0 + 60 * 1000).toString()
        )
    }

    @Test
    fun testProcessTemplate() {
        val values = mapOf(
            "1" to "One",
            "2" to "Two",
            "3" to "Three",
            "Long" to "LongValue",
            "Foo" to "Bar"
        )

        listOf(
            "#1# #2# #3#" to "One Two Three",
            "#1####2####3#" to "One#Two#Three",
            "###1####2####3###" to "#One#Two#Three#",
            "#1##Long##Foo#" to "OneLongValueBar",
            // Unclosed variables are ignored
            "#1# #2# #3" to "One Two #3",
            "#1##" to "One#"
        ).forEach { (template, expected) ->
            assertEquals(
                expected,
                OngoingActivityStatus.processTemplate(template, values).toString()
            )
        }

        // Check that when undefined values are used, returns null
        assertNull(OngoingActivityStatus.processTemplate("#NOT#", values))
    }

    @Test
    fun testMultipleTemplates() {
        val values = mapOf(
            "1" to "One",
            "2" to "Two",
            "3" to "Three",
            "Long" to "LongValue",
            "Foo" to "Bar"
        )

        // list of (list of template -> expected output)
        listOf(
            listOf("#1#", "#2#", "#3#") to "One",
            listOf("#5#", "#4#", "#3#", "#2#") to "Three",
            listOf("#1##11#", "2=#2#") to "2=Two",
        ).forEach { (templates, expected) ->
            val status = OngoingActivityStatus(
                templates,
                values.map { (k, v) ->
                    k to TextStatusPart(v)
                }.toMap()
            )

            assertEquals(expected, status.getText(context, 0).toString())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun testVerifyBuildCheckBaseTemplate() {
        // We verify on build() that the last template uses only base parts (Text & Timer)
        OngoingActivityStatus.Builder()
            .addTemplate("#1#")
            .addTemplate("#2#")
            .addPart("1", TextStatusPart("text"))
            .addPart("2", SampleStatusPart())
            .build()
    }

    @Test(expected = IllegalStateException::class)
    fun testVerifyBuildCheckTemplates() {
        // We verify on build() that all parts used on templates are present
        OngoingActivityStatus.Builder()
            .addTemplate("#1##2##3#")
            .addPart("1", TextStatusPart("text"))
            .addPart("2", TimerStatusPart(12345L))
            .build()
    }

    class SampleStatusPart : StatusPart() {
        override fun getText(context: Context, timeNowMillis: Long) = "Sample"
        override fun getNextChangeTimeMillis(fromTimeMillis: Long) = Long.MAX_VALUE
    }
}
