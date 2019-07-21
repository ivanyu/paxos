package me.ivanyu.paxos.simulation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class TimerTest {
    @Mock
    private lateinit var time: Time

    @Test
    fun `Timer should expire when timeout is reached`() {
        `when`(time.now())
                .thenReturn(0)
                .thenReturn(1)
                .thenReturn(2)
                .thenReturn(3)
        val timer = Timer(time, 3)
        assertFalse(timer.expired())
        assertFalse(timer.expired())
        assertTrue(timer.expired())
    }

    @Test
    fun `Timer should stay expired`() {
        `when`(time.now())
                .thenReturn(0)
                .thenReturn(1)
                .thenReturn(2)
                .thenReturn(3)
                .thenReturn(4)
                .thenReturn(5)
        val timer = Timer(time, 3)
        assertFalse(timer.expired())
        assertFalse(timer.expired())
        assertTrue(timer.expired())
        assertTrue(timer.expired())
        assertTrue(timer.expired())
    }

    @Test
    fun `Timer should correctly show remain`() {
        `when`(time.now())
                .thenReturn(0)
                .thenReturn(1)
                .thenReturn(2)
                .thenReturn(3)
                .thenReturn(4)
                .thenReturn(5)
        val timer = Timer(time, 3)
        assertEquals(2, timer.remain())
        assertEquals(1, timer.remain())
        assertEquals(0, timer.remain())
        assertEquals(0, timer.remain())
        assertEquals(0, timer.remain())
    }
}
