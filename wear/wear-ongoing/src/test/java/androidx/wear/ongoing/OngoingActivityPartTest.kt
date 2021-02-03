package androidx.wear.ongoing

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import androidx.versionedparcelable.ParcelUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(PatchedRobolectricTestRunner::class)
@DoNotInstrument
@Config(sdk = [Build.VERSION_CODES.Q])
open class OngoingActivityPartTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testTextOngoingActivityStatusBasic() {
        val text = "Text"
        val textStatus = TextStatusPart(text)

        assertEquals(Long.MAX_VALUE, textStatus.getNextChangeTimeMillis(0))

        assertEquals(text, textStatus.getText(context, 0))

        assertEquals(TextStatusPart(text), textStatus)
        assertNotEquals(TextStatusPart("Other"), textStatus)

        assertEquals(TextStatusPart(text).hashCode(), textStatus.hashCode())
        assertEquals(
            TextStatusPart("Other").hashCode(),
            TextStatusPart("Other").hashCode()
        )
    }

    @Test
    fun testTimerOngoingActivityStatusBasic() {
        assertEquals(
            TimerStatusPart(1234L),
            TimerStatusPart(1234L)
        )
        assertEquals(
            TimerStatusPart(1234L).hashCode(),
            TimerStatusPart(1234L).hashCode()
        )
        assertNotEquals(
            TimerStatusPart(1234L),
            TimerStatusPart(1235L)
        )

        assertEquals(
            TimerStatusPart(1234L, true),
            TimerStatusPart(1234L, true)
        )
        assertEquals(
            TimerStatusPart(1234L, true).hashCode(),
            TimerStatusPart(1234L, true).hashCode()
        )
        assertNotEquals(
            TimerStatusPart(1234L, false),
            TimerStatusPart(1234L, true)
        )

        assertEquals(
            TimerStatusPart(1234L, true, 5678L),
            TimerStatusPart(1234L, true, 5678L)
        )
        assertEquals(
            TimerStatusPart(1234L, true, 5678L).hashCode(),
            TimerStatusPart(1234L, true, 5678L).hashCode()
        )
        assertNotEquals(
            TimerStatusPart(1234L, true, 5678L),
            TimerStatusPart(1234L, true, 5679L)
        )
        assertNotEquals(
            TimerStatusPart(1234L, true, 5678L),
            TimerStatusPart(1234L, true)
        )

        assertEquals(
            TimerStatusPart(1234L, true, 5678L, 100L),
            TimerStatusPart(1234L, true, 5678L, 100L)
        )
        assertEquals(
            TimerStatusPart(1234L, true, 5678L, 100L).hashCode(),
            TimerStatusPart(1234L, true, 5678L, 100L).hashCode()
        )
        assertNotEquals(
            TimerStatusPart(1234L, true, 5678L, 100L),
            TimerStatusPart(1234L, true, 5678L, 101L)
        )
        assertNotEquals(
            TimerStatusPart(1234L, true, 5678L, 100L),
            TimerStatusPart(1234L, true, 5678L)
        )
        assertNotEquals(
            TimerStatusPart(1234L, true, 5678L, 100L),
            TimerStatusPart(1234L, true)
        )
    }

    @Test
    fun testOngoingActivityStatusSerialization() {
        val key = "KEY"
        listOf(
            TimerStatusPart(1234L),
            TextStatusPart("Text1"),
            TimerStatusPart(1234L, false),
            TimerStatusPart(1234L, true),
            TimerStatusPart(1234L, true, 5678L),
            TextStatusPart("Text2"),
            TimerStatusPart(1234L, false, 5678L, 100L)
        ).forEach { original ->
            val bundle = Bundle()
            ParcelUtils.putVersionedParcelable(bundle, key, original)

            val p = Parcel.obtain()
            p.writeParcelable(bundle, 0)
            p.setDataPosition(0)

            val receivedBundle = p.readParcelable<Bundle>(Bundle::class.java.classLoader)!!

            val received = ParcelUtils.getVersionedParcelable<StatusPart>(receivedBundle, key)!!
            assertEquals(original::class, received::class)
            assertEquals(original, received)
            assertEquals(original.hashCode(), received.hashCode())
        }
    }

    @Test
    fun testTimerOngoingActivityStatusGetters() {
        TimerStatusPart(123L).also {
            assertEquals(123L, it.timeZeroMillis)
            assertFalse(it.isPaused)
            assertFalse(it.hasTotalDuration())
        }

        TimerStatusPart(1234L, false).also {
            assertEquals(1234L, it.timeZeroMillis)
            assertFalse(it.isCountDown)
            assertFalse(it.isPaused)
            assertFalse(it.hasTotalDuration())
        }

        TimerStatusPart(12345L, true).also {
            assertEquals(12345L, it.timeZeroMillis)
            assertTrue(it.isCountDown)
            assertFalse(it.isPaused)
            assertFalse(it.hasTotalDuration())
        }

        TimerStatusPart(2345L, false, 3456L).also {
            assertEquals(2345L, it.timeZeroMillis)
            assertFalse(it.isCountDown)
            assertTrue(it.isPaused)
            assertEquals(3456L, it.pausedAtMillis)
            assertFalse(it.hasTotalDuration())
        }

        TimerStatusPart(4567L, true, 7890L, 12000L).also {
            assertEquals(4567L, it.timeZeroMillis)
            assertTrue(it.isCountDown)
            assertTrue(it.isPaused)
            assertEquals(7890L, it.pausedAtMillis)
            assertTrue(it.hasTotalDuration())
            assertEquals(12000L, it.totalDurationMillis)
        }
    }

    @Test
    fun testTimerOngoingActivityStatusChronometer() {
        // Create a chronometer, starting at the given timestamp (around 2 minutes after
        // timestamp 0).
        val t0 = 123456L
        val timerStatus =
            TimerStatusPart(/* timeZeroMillis = */ t0)

        // The chronometer is not paused.
        assertFalse(timerStatus.isPaused())

        // The chronometer will always change at timestamps ending in 456.
        assertEquals(456L, timerStatus.getNextChangeTimeMillis(0L))
        assertEquals(456L, timerStatus.getNextChangeTimeMillis(455L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(456L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(457L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(1000L))

        assertEquals(t0, timerStatus.getNextChangeTimeMillis(t0 - 1))
        assertEquals(t0 + 1000, timerStatus.getNextChangeTimeMillis(t0))

        // Check default formatting.
        assertEquals("00:00", timerStatus.getText(context, t0))
        assertEquals("00:00", timerStatus.getText(context, t0 + 999))
        assertEquals("00:01", timerStatus.getText(context, t0 + 1000))

        assertEquals("00:59", timerStatus.getText(context, t0 + 60 * 1000 - 1))
        assertEquals("01:00", timerStatus.getText(context, t0 + 60 * 1000))

        assertEquals("59:59", timerStatus.getText(context, t0 + 3600 * 1000 - 1))
        assertEquals("1:00:00", timerStatus.getText(context, t0 + 3600 * 1000))
    }

    @Test
    fun testTimerOngoingActivityStatusTimer() {
        // Create a timer, set to expire at the given timestamp (around 2 minutes after
        // timestamp 0).
        val t0 = 123456L
        val timerStatus = TimerStatusPart(
            /* timeZeroMillis = */ t0,
            /* countDown = */ true
        )

        // The Timer is not paused.
        assertFalse(timerStatus.isPaused())

        // The timer will always change at timestamps ending in 456.
        assertEquals(456L, timerStatus.getNextChangeTimeMillis(0L))
        assertEquals(456L, timerStatus.getNextChangeTimeMillis(455L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(456L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(457L))
        assertEquals(1456L, timerStatus.getNextChangeTimeMillis(1000L))

        assertEquals(t0, timerStatus.getNextChangeTimeMillis(t0 - 1))
        assertEquals(t0 + 1000, timerStatus.getNextChangeTimeMillis(t0))

        // Check default formatting.
        assertEquals("00:01", timerStatus.getText(context, t0 - 1))
        assertEquals("00:00", timerStatus.getText(context, t0))
        assertEquals("00:00", timerStatus.getText(context, t0 + 999))
        assertEquals("-00:01", timerStatus.getText(context, t0 + 1000))

        assertEquals("02:00", timerStatus.getText(context, 3456L))

        assertEquals("-1:00:00", timerStatus.getText(context, t0 + 3600 * 1000))
    }

    @Test
    fun testTimerOngoingActivityStatusChronometerPaused() {
        val t0 = 123456L
        var timerStatus = TimerStatusPart(
            /* timeZeroMillis = */ t0,
            /* countDown = */ false,
            /* pausedAt = */ t0 + 1999
        )

        // The Timer is paused.
        assertTrue(timerStatus.isPaused())
        assertEquals(t0 + 1999, timerStatus.pausedAtMillis)

        // The timer is paused, will never change.
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(0L))
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(t0))
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(t0 + 3600 * 1000))

        // Check formatting. Current Time doesn't mater.
        assertEquals("00:01", timerStatus.getText(context, 0))
        assertEquals("00:01", timerStatus.getText(context, t0))
        assertEquals("00:01", timerStatus.getText(context, t0 + 2000))
        assertEquals("00:01", timerStatus.getText(context, t0 + 3600 * 1000))
    }

    @Test
    fun testTimerOngoingActivityStatusTimerPaused() {
        val t0 = 123456L
        var timerStatus = TimerStatusPart(
            /* timeZeroMillis = */ t0,
            /* countDown = */ true,
            /* pausedAt = */ t0 + 1999
        )

        // The Timer is paused.
        assertTrue(timerStatus.isPaused())
        assertEquals(t0 + 1999, timerStatus.pausedAtMillis)

        // The timer is paused, will never change.
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(0L))
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(t0))
        assertEquals(Long.MAX_VALUE, timerStatus.getNextChangeTimeMillis(t0 + 3600 * 1000))

        // Check formatting. Current Time doesn't mater.
        assertEquals("-00:01", timerStatus.getText(context, 0))
        assertEquals("-00:01", timerStatus.getText(context, t0))
        assertEquals("-00:01", timerStatus.getText(context, t0 + 2000))
        assertEquals("-00:01", timerStatus.getText(context, t0 + 3600 * 1000))
    }
}
