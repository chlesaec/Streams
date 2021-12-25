package ui

import configuration.Config
import connectors.*
import graph.*
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Stage
import job.*
import kotlinx.serialization.Serializable
import tornadofx.*
import java.util.*
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

    operator fun div(other: Double) : Coordinate {
        return Coordinate(this.x / other, this.y / other)
    }

    fun length() : Double {
        return sqrt(this.x*this.x + this.y*this.y);
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
}

interface ElementView<T, U> {
    fun show(g : GraphicsContext, identifier : UUID, graph : Graph<T, U>)

    fun center(size : Coordinate) : Coordinate = Coordinate(0.0, 0.0)

    fun inside(c : Coordinate, size : Coordinate) : Boolean = false
}

@Serializable
class ComponentView(var position : Coordinate) : ElementView<JobConnector, JobLink> {
    override fun show(g : GraphicsContext,
                      identifier: UUID,
                      graph : Graph<JobConnector, JobLink>) {
        val desc : ConnectorDesc? = graph.nodes[identifier]?.data?.connectorDesc
        val icon : Image? = desc?.let { it.icon() }
        if (icon is Image) {
            g.drawImage(
                icon, this.position.x, this.position.y,
                icon.width, icon.height
            )
        }
    }

    override fun center(size : Coordinate) : Coordinate {
        return this.position + (size / 2.0)
    }

    override fun inside(c : Coordinate, size : Coordinate) : Boolean {
        return this.position.x < c.x && this.position.x + size.x.toInt() > c.x
                && this.position.y < c.y && this.position.y + size.y.toInt() > c.y
    }
}

@Serializable
class LinkView(val color : Color,
               val width : Double) : ElementView<JobConnector, JobLink> {
    override fun show(g : GraphicsContext,
                      identifier: UUID,
                      graph : Graph<JobConnector, JobLink>) {
        g.stroke = this.color
        g.lineWidth = this.width

        val edge: Edge<JobConnector, JobLink>? = graph.edges[identifier]
        if (edge is Edge<JobConnector, JobLink>) {
            val start: Coordinate = edge.start.data.center()
            val end: Coordinate  = edge.end.data.center()
            val middlePoint = ((start + end) / 2.0)

            val base = (end - start).unit() * 11.0
            val arrowPoint = middlePoint + base
            val basePoint = middlePoint - base
            val ortho = (arrowPoint - basePoint).ortho()

            val firstPoint = basePoint + (ortho * 11.0)
            val secondPoint = basePoint - (ortho * 11.0)
            g.strokeLine(start.x, start.y, end.x, end.y)
            g.strokeLine(firstPoint.x, firstPoint.y, arrowPoint.x, arrowPoint.y)
            g.strokeLine(secondPoint.x, secondPoint.y, arrowPoint.x, arrowPoint.y)
        }
    }
}

class JobView(val jobBuilder: JobBuilder) {
    fun show(g : GraphicsContext) {
        val job : Job = this.jobBuilder.build()
        val edges: Map<UUID, Edge<JobConnector, JobLink>> = job.graph.edges
        edges.values.forEach {
            it.data.view.show(g, it.identifier, job.graph)
        }
        val nodes: Map<UUID, Node<JobConnector, JobLink>> = job.graph.nodes;
        nodes.values.forEach {
            it.data.view.show(g, it.identifier, job.graph)
        }
    }

    fun searchComponent(c : Coordinate) : JobConnectorBuilder? {
        return this.jobBuilder.graph.nodes()
            .find {
                val icon = it.connectorDesc.icon()
                it.view.inside(c, Coordinate(icon.width, icon.height))
            }
    }
}

class NodeDragger(val canvas : Canvas,
                  val component : ComponentView,
                  val job : JobView,
                  val show : (GraphicsContext) -> Unit) {
    val dm : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
            v : MouseEvent ->  this.dragMoved(v)
    }
    val ed : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
        v : MouseEvent ->  this.endDrag(v)
    }
    init {
        this.canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)
    }

    private fun dragMoved(evt : MouseEvent) {
        component.position = Coordinate(evt.x, evt.y)
        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show(this.canvas.graphicsContext2D)
        evt.consume()
    }

    private fun endDrag(evt : MouseEvent) {
        this.canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)

        evt.consume()
    }
}

class ConfigView(val comp : JobConnectorBuilder, val description: FieldType) {

    fun buildNode() : javafx.scene.Node {
        var configBuilder = this.comp.config
        return this.buildNode("", configBuilder, this.description)

    }

    private fun buildNode(name: String, config : Config.Builder, description: FieldType) : javafx.scene.Node {
        val valueNode: javafx.scene.Node = when (description) {
            is SimpleType -> {
                var value = config.get(name)
                if (value == null) {
                    value = ""
                    config.add(name, "")
                }
                val textField = TextField(value)
                textField.onKeyTyped = EventHandler {
                    evt : KeyEvent ->
                        //println("Action handler")
                        config.add(name, textField.text)
                        println("--> '${textField.text}' to '${config.get(name)}' for '${name}' ")

                }
                HBox(Label(name), textField)
            }
            is ComposedType ->
            {
                val box = VBox(1.4,
                    Label(name),
                    *description.fields.fields().map {
                        val sub: Config.Builder = config.getSub(it.first) ?:
                        config.addSub(it.first, Config.Builder())
                        this.buildNode(it.first, sub, it.second)
                    }.toTypedArray()
                )
                box.border = Border(BorderStroke(Color.BLACK,
                    BorderStrokeStyle.SOLID,
                    CornerRadii.EMPTY,
                    BorderWidths.DEFAULT
                ))
                box
            }
        }
        return valueNode
    }
}

class StudioView() : View("studio") {
    var jobView : JobView
    var configView : ConfigView
    var selectedConnector : JobConnectorBuilder? = null
    var job : Job


    init {
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("./icon1.png")
// , Image("file:" + iconUrl.path)
        val c1View = ComponentView(Coordinate(30.0, 30.0))
        val c2View = ComponentView(Coordinate(220.0, 160.0))
        val c3View = ComponentView(Coordinate(470.0, 290.0))

        val l1 = JobLink(LinkView(Color.RED, 3.0))
        val l2 = JobLink(LinkView(Color.BLUE, 6.0))

        Image("file:" + iconUrl.path)
        val descCon = ConnectorDesc(
            VersionedIdentifier("intInc", Version(listOf(1))),
            Link(Int::class),
            Link(Int::class),
            ConfigDescription(ComposedType(Fields.Builder().add("field1", StringType()).build())),
            { Image("file:" + iconUrl.path) }
        ) { c: Config -> VoidConnector(c) }
        val c1 = JobConnectorBuilder("c1", "1", descCon, Config.Builder(), c1View)
        val c2 = JobConnectorBuilder("c2", "2", descCon, Config.Builder(), c2View)
        val c3 = JobConnectorBuilder("c3", "3s", descCon, Config.Builder(), c3View)

        val builder = GraphBuilder<JobConnectorBuilder, JobLink>();
        val node1 = builder.addNode(c1);
        node1.addNext(c2, l1).next.addNext(c3, l2)

        this.jobView = JobView( JobBuilder(builder))

        val confRec = ComposedType(
            Fields.Builder()
                .add("f1", StringType())
                .add("f2", StringType())
                .build()
        )
        val config = Config.Builder()
            .add("f1", "value1")
            .add("f2", "value2")
        this.configView = ConfigView(c1, confRec)

        val jobBuilder = GraphBuilder<JobConnector, JobLink>();

        job = Job(jobBuilder.build())
    }

    private fun draw(g : GraphicsContext) {
        this.jobView.show(g)
        val cnx = this.selectedConnector
        println("Selected connector ")
        if (cnx is JobConnectorBuilder) {

            val img = cnx.connectorDesc.icon()
            val size = Coordinate(img.width, img.height)
            val center = cnx.view.center(size)
            g.lineWidth = 3.0
            g.stroke = Color.YELLOW

            g.strokeLine(center.x - (size/2.0).x, center.y - (size/2.0).y, center.x + (size/2.0).x, center.y - (size/2.0).y)
            g.strokeLine(center.x - (size/2.0).x, center.y + (size/2.0).y, center.x + (size/2.0).x, center.y + (size/2.0).y)
            g.strokeLine(center.x + (size/2.0).x, center.y - (size/2.0).y, center.x + (size/2.0).x, center.y + (size/2.0).y)
            g.strokeLine(center.x - (size/2.0).x, center.y - (size/2.0).y, center.x - (size/2.0).x, center.y + (size/2.0).y)
        }
    }

    override val root = borderpane {
        top = hbox {
            menubar {
                menu("File")
            }
        }
        center = canvas {
            this.width = 900.0
            this.height = 600.0
            this.minWidth(900.0)
            this.minHeight(600.0)
            this.graphicsContext2D.clearRect(0.0, 0.0, this.width, this.height)
            this@StudioView.draw(this.graphicsContext2D)
            this.addEventFilter(MouseEvent.MOUSE_PRESSED, this@StudioView::startDrag)
        }
        right = this@StudioView.configView.buildNode()
    }

    private fun startDrag(evt: MouseEvent) {
        val center = root.center
        if (center is Canvas) {
            val comp : JobConnectorBuilder? = this.jobView.searchComponent(Coordinate(evt.x, evt.y))
            if (comp is JobConnectorBuilder) {
                if (evt.button.ordinal == 1) {
                    this.configView = ConfigView(comp, comp.connectorDesc.config.description)
                    root.right = this.configView.buildNode()
                    this.selectedConnector = comp
                    println("affected selected connector")
                    NodeDragger(center, comp.view as ComponentView, this.jobView, this::draw)
                }
                else if (evt.button.ordinal == 3) {
                    val item = MenuItem("Hello")
                    val menu = ContextMenu(item)
                    menu.show(center, evt.screenX, evt.screenY)
                }
            }
        }
        evt.consume()
    }
}

class Studio: App(StudioView::class) {
    override fun start(stage: Stage) {
        with(stage) {
            minWidth = 900.0
            minHeight = 600.0
            super.start(this)
        }
    }
}

class VoidConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
    }
}


fun main(args: Array<String>) {
    launch<Studio>(args)
}
