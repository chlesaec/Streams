package ui

import commons.Coordinate
import job.JobConnectorBuilder
import job.JobLink
import java.io.InputStream
import kotlin.math.abs


interface Graphics {
    fun startSelectConnector()

    fun startSelectLink()

    fun startLink(link: JobLink)

    fun drawLine(from: Coordinate, to: Coordinate)

    fun startConnector()

    fun showImage(getter: () -> InputStream, position: Coordinate)

    fun write(text: String, position: Coordinate)

    fun restore()

    fun showCounter(counter: Long, position: Coordinate)
}


class ConnectorView(val g: Graphics) {

    fun drawSelectedConnector(cnx: JobConnectorBuilder) {
        val size = cnx.connectorDesc.dimension()
        val center = cnx.view.center(size)

        g.startSelectConnector()

        val leftHigh = Coordinate(center.x - (size.x/2), center.y - (size.y/2))
        val leftLow = Coordinate(center.x - (size.x/2), center.y + (size.y/2))
        val rightHigh = Coordinate(center.x + (size.x/2), center.y - (size.y/2))
        val rightLow = Coordinate(center.x + (size.x/2), center.y + (size.y/2))

        g.drawLine(leftHigh, rightHigh)
        g.drawLine(leftLow, rightLow)
        g.drawLine(leftHigh, leftLow)
        g.drawLine(rightHigh, rightLow)
    }

    fun drawSelectedLink(from: JobConnectorBuilder, to: JobConnectorBuilder) {
        g.startSelectLink()
        g.drawLine(from.center(), to.center())
    }

    fun drawLink(start: Coordinate, end: Coordinate, link: JobLink) {
        this.g.startLink(link)
        val middlePoint = (start + end) / 2.0

        val base = (end - start).unit() * 11.0
        val arrowPoint = middlePoint + base
        val basePoint = middlePoint - base
        val ortho = (arrowPoint - basePoint).ortho()

        val firstPoint = basePoint + (ortho * 11.0)
        val secondPoint = basePoint - (ortho * 11.0)
        g.drawLine(start, end)
        g.drawLine(firstPoint, arrowPoint)
        g.drawLine(secondPoint, arrowPoint)

        val position = (start * 8.0 + end * 2.0) / 10.0
        val unitVector = (end - start).unit()

        g.write(link.name(),
            Coordinate(position.x + 10.0 * unitVector.y,
            position.y - 10.0 * abs(unitVector.x)))
    }

    fun showConnector(connector : JobConnectorBuilder) {
        g.showImage(connector.connectorDesc.iconURL::openStream, connector.view.position)
        g.startConnector()
        g.write("${connector.name}\n(${connector.connectorDesc.identifier.name})",
            connector.view.position -  Coordinate(0.0, 10.0))
    }


}
