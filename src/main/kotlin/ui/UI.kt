package ui

import configuration.Config
import connectors.*
import graph.Graph
import graph.GraphBuilder
import graph.Node
import javafx.event.EventHandler
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Stage
import kotlinx.serialization.Serializable
import tornadofx.*

@Serializable
data class Coordinate(val x : Int, val y : Int) {
    constructor(x : Double, y : Double) : this(x.toInt(), y.toInt())

    operator fun plus(other : Coordinate) : Coordinate {
        return Coordinate(this.x + other.x, this.y + other.y)
    }
}

@Serializable
class ComponentView(var position : Coordinate,
                    val icon : Image) {
    fun show(g : GraphicsContext) {
        g.drawImage(this.icon, this.position.x.toDouble(), this.position.y.toDouble(),
        this.icon.width, this.icon.height)
    }

    fun center() : Coordinate {
        return this.position + Coordinate(icon.width.toInt() / 2, icon.height.toInt() / 2)
    }
}

@Serializable
class LinkView(val color : Color, val width : Double) {
    fun show(g : GraphicsContext, from : ComponentView, to : ComponentView) {
        g.stroke = this.color
        g.lineWidth = this.width
        val start : Coordinate = from.center()
        var end : Coordinate = to.center()

        g.strokeLine(start.x.toDouble(), start.y.toDouble(), end.x.toDouble(), end.y.toDouble())
    }
}

class JobView(val jobDesc : Graph<ComponentView, LinkView>) {
    fun show(g : GraphicsContext) {
        this.jobDesc.edges.values.forEach {
            it.data.show(g, it.start.data, it.end.data)
        }
        this.jobDesc.nodes.values.forEach {
            it.data.show(g)
        }
    }

    fun searchComponent(c : Coordinate) : ComponentView? {
        return this.jobDesc.nodes.values
            .map(Node<ComponentView, LinkView>::data)
            .find {
                it.position.x < c.x && it.position.x + it.icon.width.toInt() > c.x
                        && it.position.y < c.y && it.position.y + it.icon.height.toInt() > c.y
            }
    }
}

class NodeDragger(val canvas : Canvas,
                  val component : ComponentView,
                  val job : JobView) {
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
        job.show(this.canvas.graphicsContext2D)
        evt.consume()
    }

    private fun endDrag(evt : MouseEvent) {
        this.canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)

        evt.consume()
    }
}

class ConfigView(val config : Config, val description: FieldType) {

    fun buildNode() : javafx.scene.Node {
        return this.buildNode("", this.config, this.description)
    }

    private fun buildNode(name: String, config : Config?, description: FieldType) : javafx.scene.Node {
        val valueNode: javafx.scene.Node = when (description) {
            is SimpleType ->
                HBox(Label(name), TextField(config?.get(name) ?: ""))
            is ComposedType ->
            {
                val box = VBox(1.4,
                    Label(name),
                    *description.fields.fields().map {
                        this.buildNode(it.first, config?.sub?.get(it.first), it.second)
                    }.toTypedArray()
                )
                box.border = Border(BorderStroke(Color.BLACK,
                    BorderStrokeStyle.SOLID,
                    CornerRadii.EMPTY,
                    BorderWidths.DEFAULT))
                box
            }
        }
        return valueNode
    }
}

class StudioView() : View("studio") {
    var jobView : JobView
    var configView : ConfigView
    init {
        val c1 = ComponentView(Coordinate(30, 30), Image("file:/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/icon1.png"))
        val c2 = ComponentView(Coordinate(220, 160), Image("file:/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/icon1.png"))
        val c3 = ComponentView(Coordinate(470, 290), Image("file:/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/icon1.png"))
        val l1 = LinkView(Color.RED, 3.0)
        val l2 = LinkView(Color.BLUE, 6.0)
        val builder = GraphBuilder<ComponentView, LinkView>();
        val node1 = builder.addNode(c1);
        node1.addNext(c2, l1).next.addNext(c3, l2)
        val desc = builder.build()
        this.jobView = JobView(desc)

        val confRec = ComposedType(
            Fields.Builder()
                .add("f1", StringType())
                .add("f2", StringType())
                .build()
        )
        val config : Config = Config.Builder()
            .add("f1", "value1")
            .add("f2", "value2")
            .build();
        this.configView = ConfigView(config, confRec)
    }

    private fun draw(g : GraphicsContext) {
        this.jobView.show(g)
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
            val comp : ComponentView? = this.jobView.searchComponent(Coordinate(evt.x, evt.y))
            if (comp != null) {
                NodeDragger(center, comp, this.jobView)
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

fun main(args: Array<String>) {
    launch<Studio>(args)
}
