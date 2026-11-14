package com.example.pra.data.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturesTest {

    private fun tokens(vararg primitives: GesturePrimitive) =
        primitives.map { GestureToken(it) }

    @Test
    fun `single swipe does not match repeat two`() {
        val binding = GestureBinding(
            listOf(GestureStep(GesturePrimitive.SwipeLeft, repeat = 2)),
            PlayerAction.SeekForward,
        )
        assertFalse(Gestures.matches(binding, tokens(GesturePrimitive.SwipeLeft)))
        assertTrue(
            Gestures.matches(
                binding,
                tokens(GesturePrimitive.SwipeLeft, GesturePrimitive.SwipeLeft),
            ),
        )
    }

    @Test
    fun `repeated swipe binds to its own action`() {
        val bindings = listOf(
            GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, 1)), PlayerAction.Next),
            GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, 2)), PlayerAction.SeekForward),
        )
        assertEquals(
            PlayerAction.SeekForward,
            Gestures.resolve(bindings, tokens(GesturePrimitive.SwipeLeft, GesturePrimitive.SwipeLeft)),
        )
        assertEquals(
            PlayerAction.Next,
            Gestures.resolve(bindings, tokens(GesturePrimitive.SwipeLeft)),
        )
    }

    @Test
    fun `sequence order is enforced`() {
        val binding = GestureBinding(
            listOf(
                GestureStep(GesturePrimitive.LongPress, holdMs = 500),
                GestureStep(GesturePrimitive.Tap),
            ),
            PlayerAction.PlayPause,
        )
        assertTrue(
            Gestures.matches(
                binding,
                listOf(GestureToken(GesturePrimitive.LongPress, 600), GestureToken(GesturePrimitive.Tap)),
            ),
        )
        // 顺序反了不匹配
        assertFalse(
            Gestures.matches(
                binding,
                listOf(GestureToken(GesturePrimitive.Tap), GestureToken(GesturePrimitive.LongPress, 600)),
            ),
        )
    }

    @Test
    fun `long press must reach configured duration`() {
        val binding = GestureBinding(
            listOf(GestureStep(GesturePrimitive.LongPress, holdMs = 800)),
            PlayerAction.PlayPause,
        )
        assertFalse(
            Gestures.matches(binding, listOf(GestureToken(GesturePrimitive.LongPress, 500))),
        )
        assertTrue(
            Gestures.matches(binding, listOf(GestureToken(GesturePrimitive.LongPress, 900))),
        )
    }

    @Test
    fun `extra trailing tokens do not match`() {
        val binding = GestureBinding(listOf(GestureStep(GesturePrimitive.Tap)), PlayerAction.PlayPause)
        assertFalse(Gestures.matches(binding, tokens(GesturePrimitive.Tap, GesturePrimitive.Tap)))
    }

    @Test
    fun `encode decode round trip`() {
        val encoded = Gestures.encode(Gestures.defaults)
        val decoded = Gestures.decode(encoded)
        assertEquals(Gestures.defaults, decoded)
    }

    @Test
    fun `broken payload falls back to empty`() {
        assertTrue(Gestures.decode("").isEmpty())
        assertTrue(Gestures.decode("garbage").isEmpty())
        assertTrue(Gestures.decode("Tap,1,500>NotAnAction").isEmpty())
    }

    @Test
    fun `default bindings are all resolvable`() {
        val doubleTap = tokens(GesturePrimitive.Tap, GesturePrimitive.Tap)
        assertEquals(PlayerAction.PlayPause, Gestures.resolve(Gestures.defaults, doubleTap))
        assertEquals(
            PlayerAction.SeekBackward,
            Gestures.resolve(Gestures.defaults, tokens(GesturePrimitive.SwipeLeft)),
        )
        assertEquals(
            PlayerAction.SeekForward,
            Gestures.resolve(Gestures.defaults, tokens(GesturePrimitive.SwipeRight)),
        )
        assertEquals(
            PlayerAction.Next,
            Gestures.resolve(
                Gestures.defaults,
                tokens(GesturePrimitive.SwipeLeft, GesturePrimitive.SwipeLeft),
            ),
        )
        assertNull(Gestures.resolve(Gestures.defaults, tokens(GesturePrimitive.SwipeUp)))
    }

    @Test
    fun `signature ignores action`() {
        val next = GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft)), PlayerAction.Next)
        val previous = next.copy(action = PlayerAction.Previous)
        assertEquals(Gestures.signature(next), Gestures.signature(previous))
    }

    @Test
    fun `duplicate gesture keeps only the last binding`() {
        val next = GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft)), PlayerAction.Next)
        val previous = next.copy(action = PlayerAction.Previous)
        val deduped = Gestures.dedupeKeepLast(listOf(next, previous))

        assertEquals(1, deduped.size)
        assertEquals(PlayerAction.Previous, deduped.first().action)
    }

    @Test
    fun `decode drops duplicate signatures`() {
        val next = GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft)), PlayerAction.Next)
        val previous = next.copy(action = PlayerAction.Previous)
        val decoded = Gestures.decode(Gestures.encode(listOf(next, previous)))
        assertEquals(1, decoded.size)
        assertEquals(PlayerAction.Previous, decoded.first().action)
    }
}
