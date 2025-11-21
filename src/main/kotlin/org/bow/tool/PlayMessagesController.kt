package org.bow.tool

import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.collections.FXCollections.*
import javafx.util.StringConverter
import javafx.event.ActionEvent
import javafx.beans.property.*
import javafx.beans.binding.When
import org.bow.tool.hex
import org.bow.tool.BOWDATA
import org.bow.tool.BOWCOMMAND
import org.yaml.snakeyaml.introspector.Property
import javafx.collections.ObservableList
import javafx.beans.binding.*
import javafx.beans.value.ObservableBooleanValue
import kotlin.math.abs
import kotlin.math.truncate
import kotlin.collections.copyOf
import java.lang.Float
import javafx.stage.Stage
import javafx.stage.Modality
import javafx.scene.Scene
import javafx.scene.Parent
import javafx.util.Duration
import javafx.scene.control.Tooltip
import javafx.application.Platform
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate


@OptIn(ExperimentalUnsignedTypes::class)
class PlayMessagesController {
    @FXML lateinit var buttonPlay: Button
    @FXML lateinit var buttonStep: Button

    @FXML lateinit var menuitemPlay: MenuItem
    @FXML lateinit var menuitemStep: MenuItem
    @FXML lateinit var menuitemWindowClose: MenuItem

    @FXML lateinit var checkBoxAutoAnswerPing: CheckBox
    @FXML lateinit var checkBoxWaitForHandoff: CheckBox
    @FXML lateinit var textfieldTimeDistanceMin: TextField
    @FXML lateinit var textfieldTimeDistanceMax: TextField
    @FXML lateinit var textareaLog: TextArea
    

    private val isMessage: SimpleBooleanProperty = SimpleBooleanProperty(false) 

    private val autoAnswerPing: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val aitForHandoff: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val timeDistanceMin: StringProperty = SimpleStringProperty("")    
    private val timeDistanceMax: StringProperty = SimpleStringProperty("")
    private val logPlay: StringProperty = SimpleStringProperty("")

    // Log buffering for performance
    private val logBuffer = mutableListOf<String>()
    private val maxLines = 2000
    private var logTimer: Timer? = null

    // var conversionModel: PayloadConverter = PayloadConverter({ converterModelIn -> updateUIConverter(converterModelIn) })
    // var messageModel: MessageWrapper? = null

    val _stage = Stage()
    var _handlerCurrentSelection: (() -> List<Message>)? =  null

    fun showDetail(stageOwner: Stage?, parent: Parent, handlerCurrentSelection: () -> List<Message>, handlerClosed: () -> Unit) {

        // messageModel = MessageWrapper(message, { messageModelIn -> updateUIFromModel(messageModelIn) })
        _handlerCurrentSelection = handlerCurrentSelection
        setupLogUpdater()

        fun handleCloseRequest() {
            // val alert = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to quit?", ButtonType.YES, ButtonType.NO)
            // alert.title = "Quit BOW Tool"
            // val result = alert.showAndWait()
            // if (result.get() == ButtonType.YES) {
                stopLogUpdater()
                handlerClosed() 
                _stage?.close()
            // }
        }

        if (stageOwner != null) {
            _stage.initOwner(stageOwner)
            _stage.initModality(Modality.NONE)
        }
        // if (message == null) {
        //     buttonUpdate.isDisable = true
        //     stageDetail.title = "New Message"
        // } else {
            _stage.title = "Play selected messages"
        // }
        _stage.scene = Scene(parent)
        _stage.setOnCloseRequest { event ->
                    event.consume()
                    handleCloseRequest()
        }


        menuitemWindowClose.setOnAction { _ -> handleCloseRequest() }


        menuitemWindowClose.setOnAction { _ -> handleCloseRequest() }
        menuitemPlay.setOnAction { event: ActionEvent -> playMessage() }
        menuitemStep.setOnAction { event: ActionEvent -> stepMessage() }
        buttonPlay.onAction = menuitemPlay.onAction
        buttonStep.onAction = menuitemStep.onAction
        //
        _stage.show()
    }

    // fun makeFrontWindow()
    // {
    //     _stage.toFront()
    // }

    fun playMessage() {
        val listMessages = _handlerCurrentSelection!!()
        if (listMessages!!.size == 0)
        {
            val alert = Alert(Alert.AlertType.INFORMATION, "Select the messages to play in the main screen", ButtonType.OK)
            alert.title = "No selected nessages to play"
            val result = alert.showAndWait()
        }
        else
        {
            log("Messages selected to play: ${listMessages.size}")
        }
    }

    fun stepMessage() {
        println("Step message")
    }

fun log(message: String) {
    handleLog(message)
}


private fun stopLogUpdater() {
    logTimer?.cancel()
    logTimer?.purge()
    logTimer = null
}

private fun setupLogUpdater() {
    val timer = Timer()
    timer.scheduleAtFixedRate(0, 200) {
        if (logBuffer.isNotEmpty()) {
            Platform.runLater {
                synchronized(logBuffer) {
                    val messages = logBuffer.joinToString("\n")
                    logBuffer.clear()
                    
                    textareaLog.appendText(messages + "\n")
                    
                    // Beperk aantal regels
                    val lines = textareaLog.text.split("\n")
                    if (lines.size > maxLines) {
                        textareaLog.text = lines.takeLast(maxLines).joinToString("\n")
                    }
                }
            }
        }
    }
}

private fun handleLog(message: String) {
    synchronized(logBuffer) {
        val timestamp = System.currentTimeMillis()
        val newEntry = "$timestamp: $message"
        logBuffer.add(newEntry)
    // val currentLog = logPlay.value
    // logPlay.value = if (currentLog.isNullOrEmpty()) {
    //     newEntry
    // } else {
    //     "$currentLog\n$newEntry"
    // }
    }
}



    // fun updateUIFromModel(messageModelIn: MessageWrapper) {
        // println("Updating UI from model: ${messageModelIn}")
        // start.value = Config.getInstance().starts.findById((messageModelIn.start?.id?.toInt() ?: -1))
        // isMessage.value = messageModelIn.isMessage
        // hasSource.value = messageModelIn.hasSource
        // hasCommand.value = messageModelIn.hasCommand
        // isGetData.value = messageModelIn.isGetData
        // //
        // messageType.value = messageModelIn.messageType
        // println("Target ui update: ${messageModelIn.target}")
        // target.value = messageModelIn.target
        // println("Source ui update: ${messageModelIn.source}")
        // source.value = messageModelIn.source
        // lengthMsg.value = messageModelIn.lengthMsg
        // command.value = messageModelIn.command
        // commandData.value = messageModelIn.data
        // payload.value = messageModelIn.payload.toHexString().chunked(4).joinToString(" ")
        // calculateAddCRC.value = messageModelIn.validCRC8
        // crc8.value = if (messageModelIn.crc8Calc != null) hex(messageModelIn.crc8Calc!!) else ""
        // messageRaw.value = messageModelIn.messageRaw.toHexString().chunked(4).joinToString(" ")
        // timestamp.value = messageModelIn.timestamp?.toString() ?: ""
        // comment.value = messageModelIn.comment ?: ""
        // decoded.value = messageModelIn.decoded ?: ""
        // println("Timestamp: ${messageModelIn.timestamp}")
        // println("Comment: ${messageModelIn.comment}")
    // }

    // fun updateUIConverter(converterModelIn: PayloadConverter) {
        // hexData.value = converterModelIn.hexData
        // intValue.value = converterModelIn.intValue
        // uintValue.value = converterModelIn.uintValue
        // float32Value.value = converterModelIn.float32Value
        // stringValue.value = converterModelIn.stringValue
    // }    
}
