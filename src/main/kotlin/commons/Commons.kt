package commons

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Serializable
data class Coordinate(val x : Double, val y : Double) {

    operator fun plus(other : Coordinate) : Coordinate {
        return Coordinate(this.x + other.x, this.y + other.y)
    }

    operator fun minus(other : Coordinate) : Coordinate {
        return Coordinate(this.x - other.x, this.y - other.y)
    }

    operator fun times(other: Double) : Coordinate {
        return Coordinate(this.x * other, this.y * other)
    }

    operator fun times(other: Coordinate) : Double {
        return this.x * other.x +  this.y * other.y
    }

    operator fun div(other: Double) : Coordinate {
        return Coordinate(this.x / other, this.y / other)
    }

    fun length() : Double {
        return sqrt(this * this);
    }

    fun unit() : Coordinate {
        return this / this.length()
    }

    fun ortho() : Coordinate {
        return if (this.x == 0.0) {
            Coordinate(1.0, 0.0)
        }
        else {
            val c = Coordinate(-this.y/this.x, 1.0)
            c / c.length()
        }
    }

    fun distanceToSegment(start : Coordinate, end : Coordinate) : Double {
        val delta = start - end
        val ortho = delta.ortho()
        val yOrtho = ((start.x - this.x)*delta.y*ortho.y
                + this.y*delta.y*ortho.x - ortho.y*start.y*delta.x) /
                (ortho.x*delta.y - ortho.y*delta.x)

        val xOrtho = ((start.y - this.y)*delta.x*ortho.x
                + this.x*delta.x*ortho.y - ortho.x*start.x*delta.y) /
                (ortho.y*delta.x - ortho.x*delta.y)
        val pointOrtho = Coordinate(xOrtho, yOrtho)
        val distance = if (pointOrtho.x <= max(start.x, end.x) &&
            pointOrtho.x >= min(start.x, end.x)
        ) {
            val ps = this - pointOrtho
            ps.length()
        }
        else {
            min((start - this).length(), (end - this).length())
        }
        return distance
    }
}
