package ui

import commons.Coordinate
import job.JobConnectorBuilder


interface Graphics {
    fun startSelectConnector()

    fun startSelectLink()

    fun drawLine(from: Coordinate, to: Coordinate)

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


}
