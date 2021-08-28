package ui

import javafx.stage.Stage
import tornadofx.*


class StudioView: View("studio") {
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
        }
        right = vbox {
            button("back")
            button("reset")
            button("show")
        }
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
