package main

import commons.Coordinate
import graph.GraphBuilder
import graph.UpdateGraphObserver
import job.*

object TestObserver : UpdateGraphObserver<JobConnector, JobLink> {
    override fun updatedPredecessors(current: JobConnector, nexts: List<Pair<JobConnector, JobLink>>) {
    }
}

fun buildJob() : Job {
    val c1 = ComponentView(Coordinate(30.0, 30.0))
    val c2 = ComponentView(Coordinate(220.0, 160.0))
    val c3 = ComponentView(Coordinate(470.0, 290.0))
    val l1 = LinkView(Color(255,0,0), 3.0)
    val l2 = LinkView(Color(0,0,255), 6.0)



    val graphBuilder = GraphBuilder<JobConnector, JobLink>(TestObserver)


    return Job(graphBuilder.build())

}
