package main

import graph.GraphBuilder
import javafx.scene.image.Image
import javafx.scene.paint.Color
import job.Job
import job.JobConnector
import job.JobLink
import ui.ComponentView
import ui.Coordinate
import ui.LinkView
import java.util.*

fun buildJob() : Job {
    val c1 = ComponentView(Coordinate(30.0, 30.0))
    val c2 = ComponentView(Coordinate(220.0, 160.0))
    val c3 = ComponentView(Coordinate(470.0, 290.0))
    val l1 = LinkView(Color.RED, 3.0)
    val l2 = LinkView(Color.BLUE, 6.0)



    val graphBuilder = GraphBuilder<JobConnector, JobLink>();


    return Job(graphBuilder.build())

}