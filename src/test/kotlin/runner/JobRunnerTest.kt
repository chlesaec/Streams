package runner

import commons.Coordinate
import configuration.Config
import connectors.*
import functions.InputItem
import functions.OutputFunction
import graph.Graph
import graph.GraphBuilder
import graph.UpdateGraphObserver
import javafx.scene.paint.Color
import job.*
import org.junit.jupiter.api.Test

import job.ComponentView
import org.junit.jupiter.api.Assertions

internal class JobRunnerTest {

    object TestObserver : UpdateGraphObserver<JobConnector, JobLink> {
        override fun updatedPredecessors(current: JobConnector, nexts: List<Pair<JobConnector, JobLink>>) {
        }
    }

    @Test
    fun testCompile() {
        val conf = Config.Builder()
            .add("start", "100")
            .add("end", "300")
            .add("step", "2")
            .build()
//        val view = ComponentView(Coordinate(2.0, 4.0));
        val cdata = JobConnectorData(JobConfig(), descritorIntGenerator, "input1", "id1")
        val c1 = JobConnector(cdata, conf)

        val confBis = Config.Builder()
            .add("start", "20")
            .add("end", "60")
            .add("step", "1")
            .build()
        val cIndata = JobConnectorData(JobConfig(), descritorIntGenerator, "input2", "id2")
        val c1Bis = JobConnector(cIndata, confBis)

        val graphJobBuilder: GraphBuilder<JobConnector, JobLink> = GraphBuilder(TestObserver)
        val nodeInput1 = graphJobBuilder.addNode(c1)
        val nodeInput2 = graphJobBuilder.addNode(c1Bis)

        val cdata2 = JobConnectorData(JobConfig(), descriptionInc, "inc1", "id")
        val c2 = JobConnector(cdata2, Config.Builder().build())
        val nodeIncr = graphJobBuilder.addNode(c2)

        val cdata2Bis = JobConnectorData(JobConfig(), descriptionDoubleProcessor, "double1", "id")
        val c2Bis = JobConnector(cdata2Bis, Config.Builder().build())
        val nodeDouble = graphJobBuilder.addNode(c2Bis)

        val cdata3 = JobConnectorData(JobConfig(), descriptorSUM, "target", "id")
        val c3 = JobConnector(cdata3, Config.Builder().build())
        val node3 = graphJobBuilder.addNode(c3)

        nodeInput1.addNext(nodeIncr, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeInput2.addNext(nodeIncr, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeIncr.addNext(node3, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

       // nodeInput1.addNext(nodeDouble, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
       // nodeDouble.addNext(node3, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }

    @Test
    fun testSimple() {
        val graphJobBuilder: GraphBuilder<JobConnector, JobLink> = GraphBuilder(TestObserver)

        val conf = Config.Builder()
            .add("start", "1")
            .add("end", "10")
            .add("step", "1")
            .build()
        val cdata = JobConnectorData(JobConfig(), descritorIntGenerator, "input", "id")
        val c1 = JobConnector(cdata, conf)
        val nodeIn = graphJobBuilder.addNode(c1)

        val cdata2 = JobConnectorData(JobConfig(), descriptorSUM, "output", "id")
        val c2 = JobConnector(cdata2, Config.Builder().build())
        val nodeOut = graphJobBuilder.addNode(c2)

        nodeIn.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }

    @Test
    fun testDoubleInput() {
        val graphJobBuilder: GraphBuilder<JobConnector, JobLink> = GraphBuilder(TestObserver)

        val conf = Config.Builder()
            .add("start", "1")
            .add("end", "10")
            .add("step", "1")
            .build()
        val cdata = JobConnectorData(JobConfig(), descritorIntGenerator, "name", "id")
        val c1 = JobConnector(cdata, conf)
        val nodeIn = graphJobBuilder.addNode(c1)

        val confIn2 = Config.Builder()
            .add("start", "1")
            .add("end", "4")
            .add("step", "1")
            .build()
        val cdataIn2 = JobConnectorData(JobConfig(), descritorIntGenerator, "name", "id")
        val cIn2 = JobConnector(cdataIn2, confIn2)
        val nodeIn2 = graphJobBuilder.addNode(cIn2)

        val cdata2 = JobConnectorData(JobConfig(), descriptorSUM, "name", "id")
        val c2 = JobConnector(cdata2, Config.Builder().build())
        val nodeOut = graphJobBuilder.addNode(c2)

        nodeIn2.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeIn.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }

    @Test
    fun testDoubleInputWithDouble() {
        val graphJobBuilder: GraphBuilder<JobConnector, JobLink> = GraphBuilder(TestObserver)

        val conf = Config.Builder()
            .add("start", "1")
            .add("end", "10")
            .add("step", "1")
            .build()
        val cdata = JobConnectorData(JobConfig(), descritorIntGenerator, "name", "id")
        val c1 = JobConnector(cdata, conf)
        val nodeIn = graphJobBuilder.addNode(c1)

        val confIn2 = Config.Builder()
            .add("start", "1")
            .add("end", "4")
            .add("step", "1")
            .build()
        val cdataIn2 = JobConnectorData(JobConfig(), descritorIntGenerator, "name", "id")
        val cIn2 = JobConnector(cdataIn2, confIn2)
        val nodeIn2 = graphJobBuilder.addNode(cIn2)

        val cDouble = JobConnectorData(JobConfig(), descriptionDoubleProcessor, "name", "id")
        val connDouble = JobConnector(cDouble, Config.Builder().build())
        val nodeDouble = graphJobBuilder.addNode(connDouble)

        val cdata2 = JobConnectorData(JobConfig(), descriptorSUM, "name", "id")
        val c2 = JobConnector(cdata2, Config.Builder().build())
        val nodeOut = graphJobBuilder.addNode(c2)

        nodeIn2.addNext(nodeDouble, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeIn.addNext(nodeDouble, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        nodeDouble.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }

    @Test
    fun testSeparateInput() {
        val graphJobBuilder: GraphBuilder<JobConnector, JobLink> = GraphBuilder(TestObserver)
        val conf = Config.Builder()
            .add("start", "1")
            .add("end", "10")
            .add("step", "1")
            .build()
        val cdata = JobConnectorData(JobConfig(), descritorIntGenerator, "source", "source")
        val c1 = JobConnector(cdata, conf)
        val nodeIn = graphJobBuilder.addNode(c1)

        val cDouble1 = JobConnectorData(JobConfig(), descriptionDoubleProcessor, "double1", "d1")
        val connDouble1 = JobConnector(cDouble1, Config.Builder().build())
        val nodeDouble1 = graphJobBuilder.addNode(connDouble1)

        val cDouble2 = JobConnectorData(JobConfig(), descriptionDoubleProcessor, "double2", "d2")
        val connDouble2 = JobConnector(cDouble2, Config.Builder().build())
        val nodeDouble2 = graphJobBuilder.addNode(connDouble2)

        val output = JobConnectorData(JobConfig(), descriptorSUM, "name", "id")
        val connectorOut = JobConnector(output, Config.Builder().build())
        val nodeOut = graphJobBuilder.addNode(connectorOut)

        nodeIn.addNext(nodeDouble1, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeIn.addNext(nodeDouble2, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        nodeDouble1.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))
        nodeDouble2.addNext(nodeOut, JobLink(LinkView(Color.BLUE, 3.0), JobLinkData(NextFilter("*"))))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }

    //@Test
    fun testQueues() {
        val desc1 = ConnectorDesc(
            VersionedIdentifier("intGenerator", Version(listOf(1))),
            LinkInput(arrayOf(Nothing::class)),
            LinkOutput().add("*",  Int::class),
            ConfigDescription(ComposedType(Fields.Builder().build())),
            { findImage("icon1.png") }
        ) { j: JobConnectorData,  c: Config -> IntGenerator(c) }

        val conf = Config.Builder()
            .add("start", "100")
            .add("end", "200")
            .add("step", "40")
            .build()
        val view = ComponentView(Coordinate(2.0, 4.0));
        val cdata = JobConnectorData(JobConfig(), desc1, "name", "id")
        val c1 = JobConnector(cdata, conf)


    }
}

val descritorIntGenerator = ConnectorDesc(
    VersionedIdentifier("intGenerator", Version(listOf(1))),
    LinkInput(arrayOf(Nothing::class)),
    LinkOutput().add("*",  Int::class),
    ConfigDescription(ComposedType(Fields.Builder().build())),
    { findImage("icon1.png") }
) { j: JobConnectorData,  c: Config -> IntGenerator(c) }

class IntGenerator(config : Config) : Connector(config) {

    var inEnd : Boolean = false

    override fun run(item: InputItem, output: OutputFunction) {
        assert(!inEnd)
        val start = config.get("start")?.toInt() ?: 0
        val end = config.get("end")?.toInt() ?: start + 100
        val step = config.get("step")?.toInt() ?: 1
        for (i in start..end step step) {
            output("main", i)
        }
    }

    override fun end() {
        this.inEnd = true
    }
}

class IntInc(config : Config) : Connector(config) {

    var inEnd : Boolean = false

    override fun run(item: InputItem, output: OutputFunction) {
        Assertions.assertFalse(this.inEnd, "Item in IntInc after end for ${item.connectorOrigin.name}")
        val input = item.input
        if (input is Int) {
            val next = input + 1
            output("main", next)
        }
    }

    override fun end() {
        this.inEnd = true
    }
}

val descriptionInc = ConnectorDesc(
    VersionedIdentifier("intInc", Version(listOf(1))),
    LinkInput(arrayOf(Int::class)),
    LinkOutput().add("*",  Int::class),
    ConfigDescription(ComposedType(Fields.Builder().build())),
    { findImage("icon1.png") }
) { j: JobConnectorData, c: Config -> IntInc(c) }

class IntDouble(config : Config) : Connector(config) {

    var inEnd : Boolean = false

    override fun run(item: InputItem, output: OutputFunction) {
        Assertions.assertFalse(this.inEnd, "Item in IntDouble after end for ${item.connectorOrigin.name}")
        val input = item.input
        if (input is Int) {
            val next = input * 2
            output("main", next)
        }
    }

    override fun end() {
        this.inEnd = true
    }
}

val descriptionDoubleProcessor = ConnectorDesc(
    VersionedIdentifier("intDouble", Version(listOf(1, 0))),
    LinkInput(arrayOf(Int::class)),
    LinkOutput().add("*",  Int::class),
    ConfigDescription(ComposedType(Fields.Builder().build())),
    { findImage("icon1.png") }
) { j: JobConnectorData, c: Config -> IntDouble(c) }

class IntSUM(config : Config) : Connector(config) {

    var sum = 0

    var inEnd : Boolean = false

    override fun run(item: InputItem, output: OutputFunction) {
        assert(!inEnd)
        if (item.input is Int) {
            sum += (item.input as Int)
        }
    }

    override fun end() {
        this.inEnd = true
    }
}

val descriptorSUM = ConnectorDesc(
    VersionedIdentifier("intReg", Version(listOf(1))),
    LinkInput(arrayOf(Int::class)),
    LinkOutput(),
    ConfigDescription(ComposedType(Fields.Builder().build())),
    { findImage("icon1.png") }
) { j: JobConnectorData, c: Config -> IntSUM(c) }
