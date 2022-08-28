package ui

import commons.Coordinate
import configuration.Config
import connectors.*
import connectors.db.DBDescriptor
import connectors.format.csv.CsvReaderDescriptor
import connectors.io.LocalFileDescriptor
import connectors.io.LocalFileOutputDescriptor
import connectors.loader.JobLoader
import connectors.loader.JobSaver
import connectors.logRow.LogRowDescriptor
import functions.OutputFunction
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
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import job.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import runner.JobRunner
import tornadofx.*

import java.io.File
import java.util.*
import kotlin.math.abs


class GraphicEvent(private val g : GraphicsContext) {
    val mutex = Mutex()

    fun run( f:(GraphicsContext) -> Unit) {
        runBlocking {
            mutex.withLock {
                f(this@GraphicEvent.g)
            }
        }
    }
}

class ComponentDraw(val g : GraphicsContext) {

    fun show(connector : JobConnectorBuilder) {
        //this.view.position, this.connectorDesc.icon()
        val icon = connector.connectorDesc.icon()
        val position = connector.view.position
        this.g.drawImage(
            icon, position.x, position.y,
            icon.width, icon.height
        )
        this.g.fill = Color.BLACK
        this.g.font = Font.font("Verdana", 14.0)
        this.g.fillText("${connector.name}\n(${connector.connectorDesc.identifier.name})",
            position.x,
            position.y - 10.0)
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

class LinkUI(val g : GraphicsContext,
             val ge: GraphicEvent,
             val link : JobLink,
             val startGetter : () -> Coordinate,
             val endGetter: () -> Coordinate) : LinkDrawer {

    override fun draw() {
        g.stroke = link.view.color
        g.lineWidth = link.view.width
        val start = startGetter()
        val end = endGetter()
        val middlePoint = (start + end) / 2.0

        val base = (end - start).unit() * 11.0
        val arrowPoint = middlePoint + base
        val basePoint = middlePoint - base
        val ortho = (arrowPoint - basePoint).ortho()

        val firstPoint = basePoint + (ortho * 11.0)
        val secondPoint = basePoint - (ortho * 11.0)
        g.strokeLine(start.x, start.y, end.x, end.y)
        g.strokeLine(firstPoint.x, firstPoint.y, arrowPoint.x, arrowPoint.y)
        g.strokeLine(secondPoint.x, secondPoint.y, arrowPoint.x, arrowPoint.y)

        this.g.fill = Color.BLACK
        this.g.font = Font.font("Verdana", 11.0)

        val position = (start * 8.0 + end * 2.0) / 10.0
        val unitVector = (end - start).unit()
        this.g.fillText("${link.name()}",
            position.x + 10.0 * unitVector.y,
            position.y - 10.0 * abs(unitVector.x))
        this.g.restore()
    }

    override fun updateCounter() {
        println("start update counter")
        this.link.view.count++
        val middlePoint = (startGetter() + endGetter()) / 2.0
        ge.run {
            this.g.fill = Color.WHITE
            this.g.fillRect(middlePoint.x - 5,
                middlePoint.y - 10,
                15.0,
                15.0
            )
            this.g.fill = Color.BLACK
            this.g.font = Font.font("Verdana", 8.0)
            this.g.fillText(
                 this.link.view.count.toString(),
                 middlePoint.x,
                 middlePoint.y
            )
        }

        println("end update counter")
    }
}

class JobView(val jobBuilder: () -> JobBuilder,
            val g: GraphicsContext) {
    val ge = GraphicEvent(this.g)
    fun show() {
        val builder: JobBuilder = this.jobBuilder()
        builder.graph.edges().forEach {
            this.showLink(it.second.data, it)
        }
        builder.graph.nodes().forEach {
            val draw = ComponentDraw(g)
            draw.show(it)
        }
    }

    private fun showLink(view : JobLink,
                         edge: Pair<JobNodeBuilder, JobEdgeBuilder>) {

        if (view.view.drawer == null) {
            val start = { edge.first.data.center() }
            val end = { edge.second.next.data.center() }
            view.view.drawer = LinkUI(g, this.ge, view, start, end)
        }
        val vd = view.view.drawer
        if (vd is LinkDrawer) {
            vd.draw()
        }
    }

    fun searchComponent(c : Coordinate) : JobNodeBuilder? {
        return this.jobBuilder().graph.nodesBuilder()
            .find {
                it.data.inside(c)
            }
    }

    fun searchLink(c : Coordinate) : Pair<JobNodeBuilder, JobEdgeBuilder>? {
        return this.jobBuilder().graph.edges().find {
            val start: Coordinate = it.first.data.center()
            val end: Coordinate = it.second.next.data.center()

            val distance = c.distanceToSegment(start, end)
            distance <= 10.0
        }
    }
}

class NodeDragger(
    private val canvas : Canvas,
    private val component : ComponentView,
    val show : () -> Unit) {
    private val dm : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
            v : MouseEvent ->  this.dragMoved(v)
    }
    private val ed : EventHandler<MouseEvent> = EventHandler<MouseEvent>() {
        v : MouseEvent ->  this.endDrag(v)
    }
    init {
        this.canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)
    }

    private fun dragMoved(evt : MouseEvent) {
        component.position = Coordinate(evt.x, evt.y)
        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show()
        evt.consume()
    }

    private fun endDrag(evt : MouseEvent) {
        this.canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)

        evt.consume()
    }
}

class NewLinkDragger(
    private val canvas : Canvas,
    private val nodeBuilder : JobNodeBuilder,
    val show : () -> Unit,
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
        val img = nodeBuilder.data.connectorDesc.icon()
        val size = Coordinate(img.width, img.height)
        this.startPosition = nodeBuilder.data.view.center(size)
        this.draw(this.startPosition)
    }

    private fun dragMoved(evt : MouseEvent) {
        val endPosition = Coordinate(evt.x, evt.y)
        this.draw(endPosition)
        evt.consume()
    }

    private fun draw(pos : Coordinate) {
        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show()

        ArrowView().drawArrow(this.canvas.graphicsContext2D, Color.BLACK, 3.0, this.startPosition, pos)
    }

    private fun endDrag(evt : MouseEvent) {
        this.canvas.removeEventFilter(MouseEvent.MOUSE_DRAGGED, this.dm)
        this.canvas.removeEventFilter(MouseEvent.MOUSE_RELEASED, this.ed)

        val endPosition = Coordinate(evt.x, evt.y)
        this.buildNewLink(endPosition)

        this.canvas.graphicsContext2D.clearRect(0.0, 0.0, this.canvas.width, this.canvas.height)
        this.show()

        evt.consume()
    }
}


class ConnectorsDialog(val redraw : () -> Unit,
                       ownerStage : Stage,
                       val addJob : (String) -> Unit) {
    private val stage: Stage = Stage()

    init {
        stage.title = "Connectors"
        stage.initOwner(ownerStage)
        val root = BorderPane()

        val names = FXCollections.observableArrayList(Connectors.names())
        val namesView = ListView(names)
        root.center = namesView

        val cancelButton = Button("Cancel")
        val okButton = Button("Ok")
        cancelButton.onAction = EventHandler {
            it.consume()
            this.stage.close()
        }
        okButton.onAction = EventHandler {
            val name = namesView.selectionModel.selectedItem
            addJob(name)
            this.redraw()
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
    fun showAndWait() {
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.showAndWait()
    }

}

class FilterChoices(ownerStage : Stage) {
    private val stage: Stage = Stage()

    init {
        stage.title = "Branch filter"
        stage.initOwner(ownerStage)
    }

    fun show(names: Array<String>, selectedNames : MutableList<String>) {
        val root = BorderPane()

        val observableNames : ObservableList<String> = FXCollections.observableArrayList<String>()
        names.forEach(observableNames::add)
        val namesView = ListView(observableNames)
        namesView.selectionModel.selectionMode = SelectionMode.MULTIPLE
        root.center = namesView

        val okButton = Button("Ok")

        okButton.onAction = EventHandler {
            val name: ObservableList<String> = namesView.selectionModel.selectedItems
            selectedNames.clear()
            name.forEach(selectedNames::add)
            it.consume()
            this.stage.close()
        }
        root.bottom = HBox(1.4, okButton)
        val scene = Scene(root, 550.0, 250.0)
        stage.scene = scene
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.showAndWait()
    }
}

class LinkBuilder(
    private val job : JobBuilder,
    private val startComponent : JobNodeBuilder,
    val selectedNames: (Array<String>) -> Array<String>,
    val linkBuilder : (Array<String>) -> JobLink) {

    fun newLink(endPosition : Coordinate) {

        val endComponent : JobNodeBuilder? = job.graph.nodesBuilder().filter { cnxNode: JobNodeBuilder ->
            cnxNode != startComponent
                    && cnxNode.data.inside(endPosition)
        }.firstOrNull()

       if (endComponent is NodeBuilder<JobConnectorBuilder, JobLink>) {
           val possibleLinks = this.isCompatible(startComponent, endComponent)
           if (possibleLinks.size == 1) {
               startComponent.addNext(endComponent, this.linkBuilder(possibleLinks))
           }
           else if (possibleLinks.size > 1) {
               val selectedLinks = this.selectedNames(possibleLinks)
               startComponent.addNext(endComponent, this.linkBuilder(selectedLinks))
           }
       }
    }

    private fun isCompatible(start: JobNodeBuilder, end: JobNodeBuilder) : Array<String> {
        return end.data.connectorDesc.intput.canSucceed(start.data.connectorDesc.output)
    }
}

class ConfigView(
    private val connectorBuilder : JobConnectorBuilder,
    private val description: FieldType) {

    fun buildNode() : javafx.scene.Node {
        var configBuilder = this.connectorBuilder.config
        return this.buildNode("", configBuilder, this.description)
    }

    private fun buildNode(name: String, config : Config.Builder, description: FieldType) : javafx.scene.Node {
        val valueNode: javafx.scene.Node = when (description) {
            is BooleanType -> {
                var value = config.get(name)
                if (value == null) {
                    value = "true"
                    config.add(name, "true")
                }
                val check = CheckBox()
                check.isSelected = "true".equals(value, true)
                check.onKeyTyped = EventHandler {
                        evt : KeyEvent ->
                    config.add(name, check.isSelected.toString())
                }
                HBox(Label(name), check)
            }
            is EmptyType -> {
                HBox()
            }
            is SimpleType -> {
                var value = config.get(name)
                if (value == null) {
                    value = ""
                    config.add(name, "")
                }
                val textField = TextField(value)
                textField.onKeyTyped = EventHandler {
                    evt : KeyEvent ->
                    config.add(name, textField.text)
                }
                HBox(Label(name), textField)
            }
            is ComposedType ->
            {
                val box = VBox(1.4,
                    Label(name),
                    *description.fields.fields().map {
                        val sub: Config.Builder = config.getSub(it.first) ?:
                        config // .addSub(it.first, Config.Builder())

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
    private var jobView : JobView? = null
    private var configView : ConfigView? = null
    private var selectedConnector : JobConnectorBuilder? = null
    private var selectedEdge : Pair<JobNodeBuilder, JobEdgeBuilder>? = null
    private var job : JobBuilder

    private var canvas: Canvas? = null

    init {
        val builder = GraphBuilder<JobConnectorBuilder, JobLink>()
        this.job = JobBuilder(builder)
    }

    private fun getJobView(g : GraphicsContext) : JobView {
        if (this.jobView == null) {
            this.jobView = JobView(this::job, g)
        }
        return this.jobView!!
    }

    private fun draw() {
        val c = this.canvas
        if (c == null) {
            return
        }
        val g : GraphicsContext = c.graphicsContext2D
        g.clearRect(0.0, 0.0, c.width, c.height)

        this.getJobView(g).show()
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

    private fun run() {
        //JobConfig
        this.job.build().run(JobRunner())
    }

    override val root = borderpane {
        top = hbox {
            menubar {
                menu("File") {
                    item("Load").setOnAction(this@StudioView::loadJob)
                    item("Save").setOnAction(this@StudioView::saveJob)
                }
                menu("Connectors") {
                    item("Load").setOnAction {
                        val res = this@borderpane.center
                        if (res is Canvas) {
                            val dialog = ConnectorsDialog(
                                { this@StudioView.draw() },
                                this@StudioView.currentStage ?: this@StudioView.primaryStage,
                                this@StudioView::addConnector
                            )
                            dialog.showAndWait()
                        }
                    }
                    item("Run").setOnAction {
                        println("Run")
                        this@StudioView.run()
                    }
                }
            }
        }
        center = canvas {
            this.width = 1000.0
            this.height = 800.0
            this.minWidth(1000.0)
            this.minHeight(800.0)
            this@StudioView.canvas = this
            this@StudioView.draw()

            this.addEventFilter(MouseEvent.MOUSE_PRESSED, this@StudioView::startDrag)
        }
        right = this@StudioView.configView?.buildNode() ?: HBox(Label("Empty"))
        right.minWidth(120.0)
    }

    private fun addConnector(name: String) {
        val cnx: ConnectorDesc? = Connectors.get(name)
        if (cnx is ConnectorDesc) {

            var n = name
            val names = this.job.graph.nodes()
                .map(JobConnectorBuilder::name)
                .filter { it.startsWith(name) }
                .toSet()
            var index = 1
            while (names.contains(n)) {
                n = name + index
                index++
            }


            val cnxBuild = JobConnectorBuilder(name,
                UUID.randomUUID().toString(),
                cnx,
                Config.Builder(),
                ComponentView(Coordinate(40.0,40.0)))
            this.job.graph.addNode(cnxBuild)
        }
    }

    private fun loadJob(evt : ActionEvent) {
        println("Load Job")

        val chooser = FileChooser()
        chooser.title = "Save current job"
        val extensionFilter = FileChooser.ExtensionFilter("job json file", "*.json")
        chooser.extensionFilters.add(extensionFilter)
        chooser.initialDirectory = File(System.getenv("HOME") ?: "")
        chooser.selectedExtensionFilter = extensionFilter
        val loadFile : File? = chooser.showOpenDialog(this.currentWindow)

        if (loadFile is File) {
            val content : String = loadFile.readText();
            val jsonJob: JsonElement = Json.parseToJsonElement(content)
            if (jsonJob is JsonObject) {
                this.job = JobLoader().loadJob(jsonJob)
                this.draw()
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
            val comp : JobNodeBuilder? = this.getJobView(center.graphicsContext2D).searchComponent(pointEvt)
            if (comp is JobNodeBuilder) {
                val connector = comp.data
                this.configView = ConfigView(connector, connector.connectorDesc.config.description)
                root.right = this.configView?.buildNode()
                this.selectedConnector = connector
                this.draw()
                if (evt.button.ordinal == 1) {
                    NodeDragger(center, connector.view, this::draw)
                }
                else {
                    val item = MenuItem("New Link")
                    val filter = FilterChoices(this.primaryStage)
                    val selectFunction = { list: Array<String> ->
                        val selection = mutableListOf<String>()
                        filter.show(list,selection)
                        selection.toTypedArray()
                    }
                    val lb = LinkBuilder(this.job, comp, selectFunction) { JobLink(LinkView(Color.BLACK, 3.0), NextFilter(it)) }
                    item.setOnAction { e : ActionEvent -> NewLinkDragger(center, comp, this::draw, lb::newLink) }

                    val deleteItem = MenuItem("delete")
                    deleteItem.setOnAction { e : ActionEvent ->
                        this.job.graph.removeNode(comp.identifier)
                        this.selectedConnector = null
                        this.draw()
                    }

                    val menu = ContextMenu(item, deleteItem)
                    menu.show(center, evt.screenX, evt.screenY)
                }
            }
            else {
                val edge : Pair<JobNodeBuilder, JobEdgeBuilder>? = this.getJobView(center.graphicsContext2D).searchLink(pointEvt)
                this.selectedConnector = null
                this.selectedEdge = edge
                this.draw()
                val currentEdge = this.selectedEdge
                if (currentEdge is Pair<JobNodeBuilder, JobEdgeBuilder>) {
                    if (evt.button.ordinal > 1) {
                        val deleteItem = MenuItem("delete")
                        deleteItem.setOnAction { e : ActionEvent ->
                            this.job.graph.removeEdge(currentEdge.first, currentEdge.second)
                            this.selectedEdge = null
                            this.draw()
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
            minWidth = 1200.0
            minHeight = 800.0
            super.start(this)
        }
    }
}

class VoidConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: OutputFunction) {
    }
}

fun initConectors() {
    LocalFileDescriptor
    LocalFileOutputDescriptor
    CsvReaderDescriptor
    DBDescriptor
    LogRowDescriptor
}

fun main(args: Array<String>) {
    initConectors()
    launch<Studio>(args)
}
