package ui

import configuration.Config
import connectors.*
import connectors.loader.JobLoader
import connectors.loader.JobSaver
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
import javafx.stage.FileChooser
import javafx.stage.Stage
import job.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tornadofx.*
import java.io.File
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
            pointOrtho.x >= min(start.x, end.x)) {
            val ps = this - pointOrtho
            ps.length()
        }
        else {
            min((start - this).length(), (end - this).length())
        }
        return distance
    }
}


@Serializable
class ComponentView(var position : Coordinate) {

    fun show(g : GraphicsContext,
             node: JobConnectorBuilder) {
        val icon : Image = node.connectorDesc.icon()
        g.drawImage(
            icon, this.position.x, this.position.y,
            icon.width, icon.height
        )
    }

    fun center(size : Coordinate) : Coordinate {
        return this.position + (size / 2.0)
    }

    fun inside(c : Coordinate, size : Coordinate) : Boolean {
        return this.position.x < c.x && this.position.x + size.x.toInt() > c.x
                && this.position.y < c.y && this.position.y + size.y.toInt() > c.y
    }
}

@Serializable
class LinkView(var color : Color,
               var width : Double)  {
    fun show(g : GraphicsContext,
             edge: Pair<JobNodeBuilder, JobEdgeBuilder>) {
        val start = edge.first.data.center()
        val end = edge.second.next.data.center()
        ArrowView().drawArrow(g, this.color, this.width, start, end)
    }
}

class ArrowView() {
    fun drawArrow(g : GraphicsContext, color: Color, width: Double, start : Coordinate, end : Coordinate) {
        g.stroke = color
        g.lineWidth = width
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

class JobView(val jobBuilder: JobBuilder) {
    fun show(g : GraphicsContext) {

        this.jobBuilder.graph.edges().forEach {
            it.second.data.view.show(g, it)
        }
        this.jobBuilder.graph.nodes().forEach {
            it.view.show(g, it)
        }
    }

    fun searchComponent(c : Coordinate) : JobNodeBuilder? {
        return this.jobBuilder.graph.nodesBuilder()
            .find {
                val icon = it.data.connectorDesc.icon()
                it.data.view.inside(c, Coordinate(icon.width, icon.height))
            }
    }

    fun searchLink(c : Coordinate) : Pair<JobNodeBuilder, JobEdgeBuilder>? {
        return this.jobBuilder.graph.edges().find {
            val start: Coordinate = it.first.data.center()
            val end: Coordinate = it.second.next.data.center()

            val distance = c.distanceToSegment(start, end)
            distance <= 10.0
        }
    }
}

class NodeDragger(val canvas : Canvas,
                  val component : ComponentView,
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

class NewLinkDragger(val canvas : Canvas,
                     val component : JobNodeBuilder,
                     val show : (GraphicsContext) -> Unit,
                     val buildNewLink : (Coordinate) -> Unit) {
    private val dm : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
            v : MouseEvent ->  this.dragMoved(v)
    }
    private val ed : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
            v : MouseEvent ->  this.endDrag(v)
    }
    private val startPosition : Coordinate
    init {
        this.canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)
        val img = component.data.connectorDesc.icon()
        val size = Coordinate(img.width, img.height)
        this.startPosition = component.data.view.center(size)
    }

    private fun dragMoved(evt : MouseEvent) {
        val endPosition = Coordinate(evt.x, evt.y)
        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show(this.canvas.graphicsContext2D)

        ArrowView().drawArrow(this.canvas.graphicsContext2D, Color.BLACK, 3.0, this.startPosition, endPosition)

        evt.consume()
    }

    private fun endDrag(evt : MouseEvent) {
        this.canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)

        val endPosition = Coordinate(evt.x, evt.y)
        this.buildNewLink(endPosition)

        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show(this.canvas.graphicsContext2D)

        evt.consume()
    }
}

class LinkBuilder(val job : JobBuilder,
                  val startComponent : JobNodeBuilder,
                  val linkBuilder : () -> JobLink) {
    fun newLink(endPosition : Coordinate) {

        val endComponent : NodeBuilder<JobConnectorBuilder, JobLink>? = job.graph.nodesBuilder().filter {
            cnxNode : NodeBuilder<JobConnectorBuilder, JobLink> ->

            val cnx = cnxNode.data
            val size = Coordinate(cnx.connectorDesc.icon().width, cnx.connectorDesc.icon().height)
            cnxNode != startComponent && cnx.view.inside(endPosition, size)
        }
            .firstOrNull()
       if (endComponent is NodeBuilder<JobConnectorBuilder, JobLink>) {
           startComponent.addNext(endComponent, this.linkBuilder())
       }
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
    var selectedEdge : Pair<JobNodeBuilder, JobEdgeBuilder>? = null
    var job : JobBuilder


    init {
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("./icon1.png")
// , Image("file:" + iconUrl.path)
        val c1View = ComponentView(Coordinate(30.0, 30.0))
        val c2View = ComponentView(Coordinate(220.0, 160.0))
        val c3View = ComponentView(Coordinate(470.0, 290.0))

        val l1 = JobLink(LinkView(Color.BLACK, 3.0))
        val l2 = JobLink(LinkView(Color.BLACK, 3.0))

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

        this.job = JobBuilder(builder)
        this.jobView = JobView(this.job)

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
    }

    private fun draw(g : GraphicsContext) {
        this.jobView.show(g)
        val cnx = this.selectedConnector

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
        val selectedLink = this.selectedEdge
        if (selectedLink is Pair<JobNodeBuilder, JobEdgeBuilder>) {
            val start = selectedLink.first.data.center()
            val end = selectedLink.second.next.data.center()
            g.lineWidth = 7.0
            g.stroke = Color.YELLOW
            g.strokeLine(start.x, start.y, end.x, end.y)
        }
    }

    override val root = borderpane {
        top = hbox {
            menubar {
                menu("File") {
                    menuitem("Load").setOnAction(this@StudioView::loadJob)
                    menuitem("Save").setOnAction(this@StudioView::saveJob)
                }
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

    private fun loadJob(evt : ActionEvent) {
        println("Load Job")

        val chooser = FileChooser()
        chooser.title = "Save current job"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("json", "json"))
        chooser.initialDirectory = File(System.getenv("HOME") ?: "")
        chooser.selectedExtensionFilter = FileChooser.ExtensionFilter("json", "json")
        val loadFile : File? = chooser.showOpenDialog(this.currentWindow)

        if (loadFile is File) {
            val content : String = loadFile.readText();
            val jsonJob: JsonElement = Json.parseToJsonElement(content)
            if (jsonJob is JsonObject) {
                this.job = JobLoader().loadJob(jsonJob)
                this.jobView = JobView(this.job)
            }
        }

    }

    private fun saveJob(evt : ActionEvent) {
        println("Save Job")
        val jsonJob = JobSaver().saveJob(this.job)
        val chooser = FileChooser()
        chooser.title = "Save current job"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("json", "json"))
        chooser.initialDirectory = File(System.getenv("HOME") ?: "")
        chooser.selectedExtensionFilter = FileChooser.ExtensionFilter("json", "json")
        val savedFile : File? = chooser.showSaveDialog(this.currentWindow)

        if (savedFile is File) {
            val result = jsonJob.toString()
            savedFile.writeText(result)
        }

        println("name ${savedFile?.name}")
    }

    private fun startDrag(evt: MouseEvent) {
        val center = root.center
        if (center is Canvas) {
            val pointEvt = Coordinate(evt.x, evt.y)
            val comp : JobNodeBuilder? = this.jobView.searchComponent(pointEvt)
            if (comp is JobNodeBuilder) {
                val connector = comp.data
                this.configView = ConfigView(connector, connector.connectorDesc.config.description)
                root.right = this.configView.buildNode()
                this.selectedConnector = connector
                center.graphicsContext2D.clearRect(0.0, 0.0, center.width, center.height)
                this.draw(center.graphicsContext2D)
                if (evt.button.ordinal == 1) {
                    NodeDragger(center, connector.view as ComponentView, this::draw)
                }
                else {
                    val item = MenuItem("New Link")
                    val lb = LinkBuilder(this.job, comp) { JobLink(LinkView(Color.BLACK, 3.0)) }
                    item.setOnAction { e : ActionEvent -> NewLinkDragger(center, comp, this::draw, lb::newLink) }

                    val deleteItem = MenuItem("delete")
                    deleteItem.setOnAction { e : ActionEvent ->
                        this.job.graph.removeNode(comp.identifier)
                        this.selectedConnector = null
                        center.graphicsContext2D.clearRect(0.0, 0.0, center.width, center.height)
                        this.draw(center.graphicsContext2D)
                    }

                    val menu = ContextMenu(item, deleteItem)
                    menu.show(center, evt.screenX, evt.screenY)
                }
            }
            else {
                val edge : Pair<JobNodeBuilder, JobEdgeBuilder>? = this.jobView.searchLink(pointEvt)
                this.selectedConnector = null
                this.selectedEdge = edge
                center.graphicsContext2D.clearRect(0.0, 0.0, center.width, center.height)
                this.draw(center.graphicsContext2D)
                val currentEdge = this.selectedEdge
                if (currentEdge is Pair<JobNodeBuilder, JobEdgeBuilder>) {
                    if (evt.button.ordinal > 1) {
                        val deleteItem = MenuItem("delete")
                        deleteItem.setOnAction { e : ActionEvent ->
                            this.job.graph.removeEdge(currentEdge.first, currentEdge.second)
                            this.selectedEdge = null
                            center.graphicsContext2D.clearRect(0.0, 0.0, center.width, center.height)
                            this.draw(center.graphicsContext2D)
                        }

                        val menu = ContextMenu(deleteItem)
                        menu.show(center, evt.screenX, evt.screenY)
                    }
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
