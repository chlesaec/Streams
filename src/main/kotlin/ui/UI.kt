package ui

import commons.Coordinate
import configuration.Config
import connectors.*
import connectors.format.csv.CsvReaderDescriptor
import connectors.io.LocalFileDescriptor
import connectors.io.LocalFileOutputDescriptor
import connectors.loader.JobLoader
import connectors.loader.JobSaver
import graph.GraphBuilder
import graph.NodeBuilder
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import job.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tornadofx.*
import java.io.File
import java.util.*


class ComponentDraw(val g : GraphicsContext) {

    fun show(position : Coordinate, icon : Image) {
        this.g.drawImage(
            icon, position.x, position.y,
            icon.width, icon.height
        )
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
            this.showLink(g, it.second.data.view, it)
        }
        this.jobBuilder.graph.nodes().forEach {
            val draw = ComponentDraw(g)
            it.show(draw::show)
        }
    }

    fun showLink(g : GraphicsContext,
                 view : LinkView,
                 edge: Pair<JobNodeBuilder, JobEdgeBuilder>) {
        val start = edge.first.data.center()
        val end = edge.second.next.data.center()
        ArrowView().drawArrow(g, view.color, view.width, start, end)
    }

    fun searchComponent(c : Coordinate) : JobNodeBuilder? {
        return this.jobBuilder.graph.nodesBuilder()
            .find {
                it.data.inside(c)
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


class ConnectorsDialog(ownerStage : Stage, addJob : (String) -> Unit) {
    private val stage: Stage = Stage()

    init {
        stage.title = "Connectors"
        stage.initOwner(ownerStage)
        val root = BorderPane()

        val names = FXCollections.observableArrayList(Connectors.names())
        root.center = ListView(names)

        val cancelButton = Button("Cancel")
        val okButton = Button("Ok")
        cancelButton.onAction = EventHandler {
            it.consume()
            this.stage.close()
        }
        okButton.onAction = EventHandler {
            it.consume()
            this.stage.close()
        }
        root.bottom = HBox(1.4,
            cancelButton,
            okButton)
        val scene = Scene(root, 550.0, 250.0)
        stage.scene = scene
    }

    /**
     * Show dialog modally and then return the result
     */
    fun showAndWait(): String {
        stage.initModality(Modality.APPLICATION_MODAL)

        stage.showAndWait()
        return "Hello"
    }

}

class LinkBuilder(val job : JobBuilder,
                  val startComponent : JobNodeBuilder,
                  val linkBuilder : () -> JobLink) {
    fun newLink(endPosition : Coordinate) {

        val endComponent : JobNodeBuilder? = job.graph.nodesBuilder().filter {
            cnxNode : JobNodeBuilder ->
            cnxNode != startComponent && cnxNode.data.inside(endPosition)
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
                menu("Connectors") {
                    menuitem("Load").setOnAction {
                        val dialog = ConnectorsDialog(this@StudioView.currentStage ?: this@StudioView.primaryStage, this@StudioView::addConnector)
                        dialog.showAndWait()
                    }
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

    private fun addConnector(name: String) {
        val cnx: ConnectorDesc? = Connectors.get(name)
        if (cnx is ConnectorDesc) {
            val cnxBuild = JobConnectorBuilder(name,
                UUID.randomUUID().toString(),
                cnx,
                Config.Builder(),
                ComponentView(Coordinate(3.0,10.0)))
            this.job.graph.addNode(cnxBuild)
        }
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
                    NodeDragger(center, connector.view, this::draw)
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

fun initConectors() {
    LocalFileDescriptor
    LocalFileOutputDescriptor
    CsvReaderDescriptor
}

fun main(args: Array<String>) {
    initConectors()
    launch<Studio>(args)
}
