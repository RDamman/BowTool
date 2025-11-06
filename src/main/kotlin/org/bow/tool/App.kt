package org.bow.tool

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.fxml.FXMLLoader


fun main(args: Array<String>) {
  println("Hello, World")
  Application.launch(App::class.java, *args)
}


class App : Application() {

// Fake python scripts can output data to stdout, and let Java decode it.

    override fun start(stage: Stage) {

        // Laad het FXML bestand en koppel de controller
        val fxmlLoader = FXMLLoader(javaClass.getResource("main.fxml")) // Verwijst naar je FXML bestand
        val scene = Scene(fxmlLoader.load())
        val controller: MainController = fxmlLoader.getController() // Haal je controller op
        controller._stage = stage
        stage.scene = scene
        stage.show()

        // Serial reader ? (add items on the fly?)
        // Add items on the fly? (how to do background tasks anyways?) And bootup background tasks?
        // Req/Resp are a pair, if we find a set decode together
        // Caching of decoded messages? Most repeat. Key might be pair of messages
    }

}
