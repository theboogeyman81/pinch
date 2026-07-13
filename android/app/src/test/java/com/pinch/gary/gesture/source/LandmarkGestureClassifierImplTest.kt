package com.pinch.gary.gesture.source

import com.pinch.gary.gesture.model.RawGestureType
import com.pinch.gary.vision.model.Handedness
import com.pinch.gary.vision.model.HandLandmarks
import com.pinch.gary.vision.model.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// MediaPipe Hands' 21-point index convention (see vision/model/VisionResult.kt)
private const val WRIST = 0
private const val THUMB_TIP = 4
private const val INDEX_MCP = 5
private const val INDEX_TIP = 8
private const val MIDDLE_MCP = 9

/** Plain JUnit4, no mocks — LandmarkGestureClassifierImpl is a pure, stateful function of its inputs. */
class LandmarkGestureClassifierImplTest {

    /** Neutral by default: not pinched, not grabbing, not swiping. handSpan = 0.1 (wrist→middleMcp). */
    private fun hand(
        thumbTip: NormalizedLandmark = NormalizedLandmark(5f, 5f, 0f),
        indexTip: NormalizedLandmark = NormalizedLandmark(-5f, -5f, 0f),
        indexMcp: NormalizedLandmark = NormalizedLandmark(5f, 5f, 0f)
    ): HandLandmarks {
        val points = MutableList(21) { NormalizedLandmark(5f, 5f, 0f) }
        points[WRIST] = NormalizedLandmark(0f, 0f, 0f)
        points[MIDDLE_MCP] = NormalizedLandmark(0f, 0.1f, 0f)
        points[THUMB_TIP] = thumbTip
        points[INDEX_TIP] = indexTip
        points[INDEX_MCP] = indexMcp
        return HandLandmarks(points, Handedness.RIGHT)
    }

    private fun pinchedHand() = hand(
        thumbTip = NormalizedLandmark(0f, 0f, 0f),
        indexTip = NormalizedLandmark(0f, 0.01f, 0f)
    )

    @Test
    fun `quick pinch-release emits Pinch after the double-pinch window elapses`() {
        val classifier = LandmarkGestureClassifierImpl()

        classifier.classify(listOf(pinchedHand()), 0L)
        val onRelease = classifier.classify(listOf(hand()), 100L)
        assertTrue(onRelease.none { it.type == RawGestureType.PINCH })

        val afterWindow = classifier.classify(emptyList(), 600L)
        assertEquals(listOf(RawGestureType.PINCH), afterWindow.map { it.type })
    }

    @Test
    fun `sustained pinch spanning 500ms emits HoldPinch exactly once, never Pinch`() {
        val classifier = LandmarkGestureClassifierImpl()
        val pinched = pinchedHand()

        classifier.classify(listOf(pinched), 0L)
        classifier.classify(listOf(pinched), 200L)
        val holdEvents = classifier.classify(listOf(pinched), 550L)
        assertEquals(listOf(RawGestureType.HOLD_PINCH), holdEvents.map { it.type })

        val moreHold = classifier.classify(listOf(pinched), 700L)
        assertTrue(moreHold.none { it.type == RawGestureType.HOLD_PINCH })

        val onRelease = classifier.classify(listOf(hand()), 900L)
        assertTrue(onRelease.none { it.type == RawGestureType.PINCH })
    }

    @Test
    fun `two quick pinches within 400ms emit DoublePinch, not two Pinches`() {
        val classifier = LandmarkGestureClassifierImpl()
        val pinched = pinchedHand()

        classifier.classify(listOf(pinched), 0L)
        classifier.classify(listOf(hand()), 100L) // first release deferred, no Pinch yet

        classifier.classify(listOf(pinched), 150L)
        val secondRelease = classifier.classify(listOf(hand()), 300L)
        assertEquals(listOf(RawGestureType.DOUBLE_PINCH), secondRelease.map { it.type })

        // deferred single Pinch must never fire after being upgraded to DoublePinch
        val afterWindow = classifier.classify(emptyList(), 800L)
        assertTrue(afterWindow.none { it.type == RawGestureType.PINCH })
    }

    @Test
    fun `large rightward centroid displacement emits SwipeRight`() {
        val classifier = LandmarkGestureClassifierImpl()
        classifier.classify(listOf(hand(indexMcp = NormalizedLandmark(0f, 0f, 0f))), 0L)
        val events = classifier.classify(listOf(hand(indexMcp = NormalizedLandmark(0.05f, 0f, 0f))), 100L)

        assertEquals(listOf(RawGestureType.SWIPE_RIGHT), events.map { it.type })
    }

    @Test
    fun `large leftward centroid displacement emits SwipeLeft`() {
        val classifier = LandmarkGestureClassifierImpl()
        classifier.classify(listOf(hand(indexMcp = NormalizedLandmark(0.1f, 0f, 0f))), 0L)
        val events = classifier.classify(listOf(hand(indexMcp = NormalizedLandmark(0.02f, 0f, 0f))), 100L)

        assertEquals(listOf(RawGestureType.SWIPE_LEFT), events.map { it.type })
    }
}
