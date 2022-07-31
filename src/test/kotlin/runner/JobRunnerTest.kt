package runner

import commons.Coordinate
import configuration.Config
import connectors.*
import graph.Graph
import graph.GraphBuilder
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color
import job.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import job.ComponentView
import java.util.*

internal class JobRunnerTest {

    @Test
    fun compile() {
        val desc1 = ConnectorDesc(
            VersionedIdentifier("intGenerator", Version(listOf(1))),
            Link(arrayOf(Nothing::class)),
            Int::class,
            ConfigDescription(ComposedType(Fields.Builder().build())),
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png")) }
        ) { j: JobConnectorData,  c: Config -> IntGenerator(c) }

        val conf = Config.Builder()
            .add("start", "100")
            .add("end", "300")
            .add("step", "2")
            .build()
        val view = ComponentView(Coordinate(2.0, 4.0));
        val cdata = JobConnectorData(JobConfig(), desc1, "name", "id")
        val c1 = JobConnector(cdata, conf)

        val confBis = Config.Builder()
            .add("start", "20")
            .add("end", "60")
            .add("step", "1")
            .build()
        val c1Bis = JobConnector(cdata, confBis)

        val graphJobBuilder = GraphBuilder<JobConnector, JobLink>()
        val node1 = graphJobBuilder.addNode(c1)
        val node1Bis = graphJobBuilder.addNode(c1Bis)

        val desc2 = ConnectorDesc(
            VersionedIdentifier("intInc", Version(listOf(1))),
            Link(arrayOf(Int::class)),
            Int::class,
            ConfigDescription(ComposedType(Fields.Builder().build())),
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png")) }
        ) { j: JobConnectorData, c: Config -> IntInc(c) }
        val cdata2 = JobConnectorData(JobConfig(), desc2, "name", "id")
        val c2 = JobConnector(cdata2, Config.Builder().build())
        val node2 = graphJobBuilder.addNode(c2)

        val desc2Bis = ConnectorDesc(
            VersionedIdentifier("intDouble", Version(listOf(1, 0))),
            Link(arrayOf(Int::class)),
            Int::class,
            ConfigDescription(ComposedType(Fields.Builder().build())),
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png")) }
        ) { j: JobConnectorData, c: Config -> IntDouble(c) }
        val cdata2Bis = JobConnectorData(JobConfig(), desc2Bis, "name", "id")
        val c2Bis = JobConnector(cdata2Bis, Config.Builder().build())
        val node2Bis = graphJobBuilder.addNode(c2Bis)

        val desc3 = ConnectorDesc(
            VersionedIdentifier("intReg", Version(listOf(1))),
            Link(arrayOf(Int::class)),
            Nothing::class,
            ConfigDescription(ComposedType(Fields.Builder().build())),
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png")) }
        ) { j: JobConnectorData, c: Config -> IntReg(c) }
        val cdata3 = JobConnectorData(JobConfig(), desc3, "name", "id")
        val c3 = JobConnector(cdata3, Config.Builder().build())
        val node3 = graphJobBuilder.addNode(c3)

        node1.addNext(node2, JobLink(LinkView(Color.BLUE, 3.0)))
        node1Bis.addNext(node2, JobLink(LinkView(Color.BLUE, 3.0)))
        node2.addNext(node3, JobLink(LinkView(Color.BLUE, 3.0)))

        node1.addNext(node2Bis, JobLink(LinkView(Color.BLUE, 3.0)))
        node2Bis.addNext(node3, JobLink(LinkView(Color.BLUE, 3.0)))

        val graphJob : Graph<JobConnector, JobLink> = graphJobBuilder.build()
        val job = Job(graphJob)
        job.run(JobRunner())
    }
}

class IntGenerator(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        val start = config.get("start")?.toInt() ?: 0
        val end = config.get("end")?.toInt() ?: start + 100
        val step = config.get("step")?.toInt() ?: 1
        for (i in start..end step step) {
            println("generate $i")
            output(i)
        }
    }
}

class IntInc(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is Int) {
            val next = input + 1
            println("add ${next}")
            output(next)
        }
    }
}

class IntDouble(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is Int) {
            val next = input * 2
            println("double ${next}")
            output(next)
        }
    }
}

class IntReg(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is Int) {
            println("END With ${input}")
        }
    }
}
