package ui

import commons.Coordinate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class CoordinateTest {

    @Test
    fun plus() {
        val result = Coordinate(2.0, -3.0) + Coordinate(5.0, 1.0)
        Assertions.assertEquals(Coordinate(7.0, -2.0), result)
    }

    @Test
    fun minus() {
        val result = Coordinate(2.0, -3.0) - Coordinate(5.0, 1.0)
        Assertions.assertEquals(Coordinate(-3.0, -4.0), result)
    }

    @Test
    fun times() {
        val result = Coordinate(2.0, -3.0) * 3.0
        Assertions.assertEquals(Coordinate(6.0, -9.0), result)
/*
        val result2 = -2.0 * Coordinate(2.0, -3.0)
        Assertions.assertEquals(Coordinate(-4.0, 6.0), result)
 */
    }

    @Test
    fun length() {
        val result = Coordinate(4.0, -3.0).length()
        Assertions.assertEquals(5.0, result)
    }

    @Test
    fun ortho() {
        val first = Coordinate(4.0, -3.0)
        val result = first.ortho()
        Assertions.assertEquals(0.0, result * first, 0.00001)

        val sec = Coordinate(0.0, -3.0)
        val result2 = sec.ortho()
        Assertions.assertEquals(0.0, result2 * sec, 0.00001)
    }

    @Test
    fun distanceToSegment() {
        val H1 = Coordinate(1.0, 1.0)
        val H2 = Coordinate(6.0, 1.0)
        val pointOnSegment = Coordinate(3.0, 1.0)
        val dist = pointOnSegment.distanceToSegment(H1, H2)
        Assertions.assertEquals(0.0, dist, 0.00001)
        val dist2 = pointOnSegment.distanceToSegment(H2, H1)
        Assertions.assertEquals(0.0, dist2, 0.00001)

        val pointOut = Coordinate(3.0, 2.0)
        val distOut = pointOut.distanceToSegment(H1, H2)
        Assertions.assertEquals(1.0, distOut, 0.00001)
        val distOut2 = pointOut.distanceToSegment(H2, H1)
        Assertions.assertEquals(1.0, distOut2, 0.00001)

        val B2 = Coordinate(5.0, 5.0)
        val pointOnB2 = Coordinate(3.0, 3.0)
        val dist3 = pointOnB2.distanceToSegment(H1, B2)
        Assertions.assertEquals(0.0, dist3, 0.00001)
        val dist3Bis = pointOnB2.distanceToSegment(B2, H1)
        Assertions.assertEquals(0.0, dist3Bis, 0.00001)

        val V2 = Coordinate(1.0, 6.0)
        val pointOnSegmentV= Coordinate(1.0, 4.0)
        val distV = pointOnSegmentV.distanceToSegment(H1, V2)
        Assertions.assertEquals(0.0, distV, 0.00001)
        val distV2 = pointOnSegmentV.distanceToSegment(V2, H1)
        Assertions.assertEquals(0.0, distV2, 0.00001)

        val pointOutV = Coordinate(0.0, 4.0)
        val distOutV = pointOutV.distanceToSegment(H1, V2)
        Assertions.assertEquals(1.0, distOutV, 0.00001)
        val distOutV2 = pointOutV.distanceToSegment(V2, H1)
        Assertions.assertEquals(1.0, distOutV2, 0.00001)
    }
}
