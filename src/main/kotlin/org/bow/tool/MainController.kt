package org.bow.tool

import javafx.fxml.FXML
import javafx.event.ActionEvent
import javafx.application.Platform
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.beans.binding.Bindings
import javafx.beans.binding.Bindings.`when`
import javafx.scene.input.MouseEvent
import javafx.util.StringConverter
import java.util.function.Predicate
import javafx.collections.transformation.FilteredList
import javafx.collections.FXCollections.emptyObservableList
import javafx.collections.FXCollections.observableArrayList
import com.fazecast.jSerialComm.SerialPort
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.fxml.FXMLLoader
import javafx.beans.property.SimpleStringProperty
import javafx.scene.input.ClipboardContent
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import java.io.File
import java.awt.Window
import javafx.beans.property.SimpleBooleanProperty


@OptIn(ExperimentalUnsignedTypes::class)
class MainController {

    var _file: File? = null
        set(value) {
            field = value
            val sName = "BOWTool 0.8.2-Snapshot"
            if (value != null) {
                _stage?.title = "$sName - ${value.name}"
            } else {
                _stage?.title = sName
            }
        }

    // File Menu
    @FXML lateinit var menuItemFileClear: MenuItem
    @FXML lateinit var menuItemFileOpen: MenuItem
    @FXML lateinit var menuItemFileOpenBin: MenuItem
    @FXML lateinit var menuItemFileOpenHex: MenuItem
    @FXML lateinit var menuItemFileAdd: MenuItem
    @FXML lateinit var menuItemFileSave: MenuItem
    @FXML lateinit var menuItemFileSaveAs: MenuItem
    @FXML lateinit var menuItemFileSaveSelection: MenuItem
    @FXML lateinit var menuItemPreferences: MenuItem
    @FXML lateinit var menuItemExit: MenuItem
    @FXML fun menuItemFileSaveAction(event: ActionEvent) {
        if (_file == null) {
            menuItemFileSaveAsAction(event)
        } else {
            writeToFile(_file!!, _messages.toList())
        }
    }
    @FXML fun menuItemFileSaveAsAction(event: ActionEvent) {
        val file = saveFile("Save messages to file", _messages.toList())
                if ((file != null) && (_file == null)) {
                    _file = file
                }
    }
    @FXML fun menuItemFileSaveSelectionAction(event: ActionEvent) {
        saveFile("Save selected messages to file", tableViewMessages.selectionModel.selectedItems)
    }
    @FXML fun menuItemPreferencesAction(event: ActionEvent) {}
    @FXML fun menuItemExitAction(event: ActionEvent) {
        handleCloseRequest()
    }
    @FXML fun menuItemFileClearAction(event: ActionEvent) {
        val alert = Alert(Alert.AlertType.CONFIRMATION, "Clear all current messages?", ButtonType.YES, ButtonType.NO)
        alert.title = "Confirm clearing all messages"
        val result = alert.showAndWait()
        if (result.get() == ButtonType.YES) {
            clearMessages()
            _file = null
        }
    }

    @FXML lateinit var anchorPaneMain: AnchorPane
    @FXML lateinit var anchorPaneCommunication: AnchorPane
    @FXML lateinit var anchorPaneMessages: AnchorPane
    @FXML lateinit var anchorPaneMessageFilter: AnchorPane
    // @FXML lateinit var scrollPaneTabVwMessage: ScrollPane

    // Edit Menu
    @FXML lateinit var menuItemNew: MenuItem
    @FXML lateinit var menuItemEdit: MenuItem
    @FXML lateinit var menuItemCut: MenuItem
    @FXML lateinit var menuItemCopy: MenuItem
    @FXML lateinit var menuItemPaste: MenuItem
    @FXML lateinit var menuItemDelete: MenuItem
    @FXML lateinit var menuItemSelectAll: MenuItem


    @FXML fun menuItemNewAction(event: ActionEvent) {
        messageDetailEdit(null)
    }

     @FXML fun menuItemEditAction(event: ActionEvent) {
        val selectedMessage = tableViewMessages.selectionModel.selectedItem
        messageDetailEdit(selectedMessage)
     }


    @FXML fun menuItemCutAction(event: ActionEvent) {
        // copy to clipboard
        val listMessages = tableViewMessages.selectionModel.selectedItems
        copySelectedToClipboard(listMessages)
        // Handle cut action
        _messages.removeAll(listMessages)
    }
    @FXML fun menuItemCopyAction(event: ActionEvent) {
        // copy to clipboard
        val listMessages = tableViewMessages.selectionModel.selectedItems
        copySelectedToClipboard(listMessages)
    }
    @FXML fun menuItemPasteAction(event: ActionEvent) {
        val listMessages = messageFromClipboard()
        _messages.addAll(listMessages)
    }
    @FXML fun menuItemDeleteAction(event: ActionEvent) {
        val alert = Alert(Alert.AlertType.CONFIRMATION, "Delete selected messages?", ButtonType.YES, ButtonType.NO)
        alert.title = "Confirm deleting selected messages"
        val result = alert.showAndWait()
        if (result.get() == ButtonType.YES) {
            val selected = tableViewMessages.selectionModel.selectedItems
            _messages.removeAll(selected)
            tableViewMessages.itemsProperty().set(filtered(_messages, _filterPredicate))
        }
    }
    @FXML fun menuItemSelectAllAction(event: ActionEvent) {
        tableViewMessages.selectionModel.selectAll()
    }

    @FXML lateinit var tableViewMessages: javafx.scene.control.TableView<Message>
    @FXML lateinit var tableColumnMsgsId: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsTime: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsRawData: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsType: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsLengthCrc: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsSource: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsTarget: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsDecoded: TableColumn<Message, String>
    @FXML lateinit var tableColumnMsgsComment: TableColumn<Message, String>


    @FXML lateinit var menuItemPlayMessages: MenuItem

    // Command > Pair
    @FXML lateinit var menuItemPairBattery: MenuItem
    @FXML lateinit var menuItemPairDisplay: MenuItem


    // Command > Scan
    @FXML lateinit var menuItemScanBattery: MenuItem
    @FXML lateinit var menuItemScanDisplay: MenuItem
    @FXML lateinit var menuItemScanMotor: MenuItem

    // Command > Execute
    @FXML lateinit var menuItemClearError0003: MenuItem
    @FXML lateinit var menuItemExecuteCustom: MenuItem

    // Communication section
    @FXML lateinit var comboBoxBaudrate: javafx.scene.control.ComboBox<Int>
    @FXML lateinit var comboBoxComPort: javafx.scene.control.ComboBox<SerialPort>
    @FXML lateinit var buttonConnect: Button
    @FXML lateinit var buttonLogMessages: Button
    @FXML lateinit var textFieldStatus: TextField

    // Source Filter section
    @FXML lateinit var checkBoxSrcMotor: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcDisplay: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcControlPad: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcBattery: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcTorqueSensor: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcPC: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcCharger: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxSrcOther: javafx.scene.control.CheckBox
    @FXML lateinit var buttonSrcClear: Button

    // Target Filter section
    @FXML lateinit var checkBoxTgtMotor: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtDisplay: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtControlPad: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtBattery: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtTorqueSensor: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtPC: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtCharger: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxTgtOther: javafx.scene.control.CheckBox
    @FXML lateinit var buttonTgtClear: Button

    // Message Type section
    @FXML lateinit var checkBoxRequest: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxResponse: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxHandOff: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxPing: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxPong: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxMsgOther: javafx.scene.control.CheckBox
    @FXML lateinit var buttonMsgClear: Button

    // Quality section
    @FXML lateinit var checkBoxCRCOK: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxLengthOK: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxCRCError: javafx.scene.control.CheckBox
    @FXML lateinit var checkBoxLengthError: javafx.scene.control.CheckBox
    @FXML lateinit var buttonQualityClear: Button


    fun handleCloseRequest() {
        val alert = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to quit?", ButtonType.YES, ButtonType.NO)
        alert.title = "Quit BOW Tool"
        val result = alert.showAndWait()
        if (result.get() == ButtonType.YES) {
            _stage?.close()
        }
    }

    var _stage: Stage? = null
        set(value) {
            field = value
            if (value != null) {
                _file = null
                value.setOnCloseRequest { event ->
                    event.consume()
                    handleCloseRequest()
                }
            }
        }
    var _config = Config.getInstance()
    val _fileChooser = FileChooser()
    val _extFilterHex = FileChooser.ExtensionFilter("HEX files (*.txt, *.hex, *.log)", "*.txt", "*.hex", "*.log")
    val _extFilterBin = FileChooser.ExtensionFilter("BIN files (*.bin)", "*.bin")
    val _extFilterBow = FileChooser.ExtensionFilter("BOW files (*.bow)", "*.bow")

    var _sDirectory: String = java.io.File(".").getCanonicalPath()
    //val _decoder = Decoder(Config.getInstance())
    var initFilter: (bSet: Boolean) -> Unit = { _: Boolean ->  } // Placeholder for initialization (set later in initialize(
    
    fun clearMessages() {
        _messages.clear()
        tableViewMessages.itemsProperty().set(filtered(_messages, _filterPredicate))
        Message.resetId()
        //
        initFilter(true)
    }

    var _messages: ObservableList<Message> = observableArrayList()

    fun setMessages(messages: List<Message>) {
        _messages.setAll(messages)
        //tableViewMessages.itemsProperty().set(filtered(_messages, _filterPredicate))
    }

    fun addMessages(messages: List<Message>) {
        _messages.addAll(messages)
        //tableViewMessages.itemsProperty().set(filtered(_messages, _filterPredicate))
    }

    // dummy initial value, will be set in initialize()
    var _filterPredicate: ObservableValue<Predicate<Message>> = Bindings.createObjectBinding({ Predicate<Message> { _ -> true } })




    fun initialize() {
        println("MainController after FXML load")
        println(_config.toString())

        _fileChooser.extensionFilters.add(_extFilterHex)
        _fileChooser.extensionFilters.add(_extFilterBin)


        _filterPredicate = Bindings.createObjectBinding(
                {
                    Predicate<Message> { message ->
                        when (message.src()) {
                            null -> true
                            BOWDEVICE.UNKNOWN.id -> checkBoxSrcOther.isSelected
                            BOWDEVICE.MOTOR.id -> checkBoxSrcMotor.isSelected
                            BOWDEVICE.DISPLAY.id -> checkBoxSrcDisplay.isSelected
                            BOWDEVICE.KEYPAD.id -> checkBoxSrcControlPad.isSelected
                            BOWDEVICE.BATTERY.id -> checkBoxSrcBattery.isSelected
                            BOWDEVICE.TORQUESENSOR.id -> checkBoxSrcTorqueSensor.isSelected
                            BOWDEVICE.PC.id -> checkBoxSrcPC.isSelected
                            BOWDEVICE.CHARGER.id -> checkBoxSrcCharger.isSelected
                            else -> checkBoxSrcOther.isSelected
                        }
                    }.and { message ->
                        when (message.tgt()) {
                            BOWDEVICE.UNKNOWN.id -> checkBoxTgtOther.isSelected
                            BOWDEVICE.MOTOR.id -> checkBoxTgtMotor.isSelected
                            BOWDEVICE.DISPLAY.id -> checkBoxTgtDisplay.isSelected
                            BOWDEVICE.KEYPAD.id -> checkBoxTgtControlPad.isSelected
                            BOWDEVICE.BATTERY.id -> checkBoxTgtBattery.isSelected
                            BOWDEVICE.TORQUESENSOR.id -> checkBoxTgtTorqueSensor.isSelected
                            BOWDEVICE.PC.id -> checkBoxTgtPC.isSelected
                            BOWDEVICE.CHARGER.id -> checkBoxTgtCharger.isSelected
                            else -> checkBoxTgtOther.isSelected
                        }
                    }.and { message ->
                        when (message.type) {
                            BOWTYPE.HANDOFF.id -> checkBoxHandOff.isSelected
                            BOWTYPE.REQUEST.id -> checkBoxRequest.isSelected
                            BOWTYPE.RESPONSE.id -> checkBoxResponse.isSelected
                            BOWTYPE.PONG.id -> checkBoxPong.isSelected
                            BOWTYPE.PING.id -> checkBoxPing.isSelected
                            else -> checkBoxMsgOther.isSelected
                        }
                    }
                },
                checkBoxSrcMotor.selectedProperty(),
                checkBoxSrcDisplay.selectedProperty(),
                checkBoxSrcControlPad.selectedProperty(),
                checkBoxSrcBattery.selectedProperty(),
                checkBoxSrcTorqueSensor.selectedProperty(),
                checkBoxSrcPC.selectedProperty(),
                checkBoxSrcCharger.selectedProperty(),
                checkBoxSrcOther.selectedProperty(),
                checkBoxTgtMotor.selectedProperty(),
                checkBoxTgtDisplay.selectedProperty(),
                checkBoxTgtControlPad.selectedProperty(),
                checkBoxTgtBattery.selectedProperty(),
                checkBoxTgtTorqueSensor.selectedProperty(),
                checkBoxTgtPC.selectedProperty(),
                checkBoxTgtCharger.selectedProperty(),
                checkBoxTgtOther.selectedProperty(),
                checkBoxRequest.selectedProperty(),
                checkBoxResponse.selectedProperty(),
                checkBoxHandOff.selectedProperty(),
                checkBoxPing.selectedProperty(),
                checkBoxPong.selectedProperty(),
                checkBoxMsgOther.selectedProperty()
                    /*.and { message ->
                        buttonCheck.isSelected || !message.isCmd(0x22)
                    }.and { message ->
                        displayUpdate.isSelected || !(message.isCmd(0x26) || message.isCmd(0x27) || message.isCmd(0x28))
                    }.and { message ->
                        invalid.isSelected || decoder.check(message).isEmpty()
                    }.and { message ->
                        !getData.isSelected || message.isCmd(0x08)
                    }.and { message ->
                        !putData.isSelected || message.isCmd(0x09)
                    }
                 buttonCheck.selectedProperty(),
                displayUpdate.selectedProperty(),
                invalid.selectedProperty(),
                getData.selectedProperty(),
                putData.selectedProperty()*/
            )
            tableViewMessages.itemsProperty().set(filtered(_messages, _filterPredicate))
            tableViewMessages.selectionModel.selectionMode = SelectionMode.MULTIPLE
            tableViewMessages.setOnMouseClicked { event ->
                if (event.clickCount == 2) {
                    messageDetailEdit(tableViewMessages.selectionModel.selectedItem)
                }
            }
            tableViewMessages.setOnKeyPressed { event ->
                if (event.code == javafx.scene.input.KeyCode.ENTER) {
                    messageDetailEdit(tableViewMessages.selectionModel.selectedItem)
                    event.consume()
                }
            }

            // COM Port
            comboBoxComPort.setConverter(object : StringConverter<SerialPort>() {
                    override fun toString(port: SerialPort?): String? {
                        return if (port == null) "-" else port.systemPortName
                    }

                    override fun fromString(string: String?): SerialPort? {
                        return null
                    }
                })
            comboBoxComPort.setOnShowing{ event ->
                    val current = comboBoxComPort.value
                    comboBoxComPort.setItems(observableArrayList(SerialPort.getCommPorts().toList()))
                    if (current != null) {
                        val match = comboBoxComPort.items.find { it.systemPortName == current.systemPortName }
                        if (match != null) {
                            comboBoxComPort.value = match
                        } else {
                            comboBoxComPort.value = null
                        }
                    }
                }
            comboBoxComPort.setItems(observableArrayList(SerialPort.getCommPorts().toList()))
            comboBoxComPort.selectionModel.select(0)
            
            // Baudrate
            comboBoxBaudrate.setItems(observableArrayList(9600, 19200))
            comboBoxBaudrate.selectionModel.select(1)

            //     
            val doOp: (() -> List<Message>) -> Unit = { op ->
                    /*tableViewMessages.itemsProperty().set(observableArrayList())
                    val task = object : Task<Unit>() {
                        override fun call() {
                            tableViewMessages.itemsProperty().set(filtered(observableArrayList(op()), _filterPredicate))
                        }
                    }*/
                    //setMessages(observableArrayList())
                    if (isMonitoring.get()) {
                        threadMonitor = null
                    }
                    val task = object : Task<Unit>() {
                        override fun call() {
                            addMessages(op())
                        }
                    }
                    Thread(task).start()
                }

            // menu items acties    
            menuItemPlayMessages.setOnAction { event -> playMessages() }

            menuItemPairBattery.setOnAction { event -> doOp({ BatteryPairer(comboBoxComPort.value, comboBoxBaudrate.value).exec() }) }
            menuItemPairDisplay.setOnAction { event -> doOp({ DisplayPairer(comboBoxComPort.value, comboBoxBaudrate.value, 1).exec() }) }

            menuItemScanBattery.setOnAction { event -> doOp({ Scanner(comboBoxComPort.value, comboBoxBaudrate.value, BOWDEVICE.BATTERY.id, Config.refreshConfig()).exec() }) }
            menuItemScanDisplay.setOnAction { event -> doOp({ Scanner(comboBoxComPort.value, comboBoxBaudrate.value, BOWDEVICE.DISPLAY.id, Config.refreshConfig()).exec() }) }
            menuItemScanMotor.setOnAction { event -> doOp({ Scanner(comboBoxComPort.value, comboBoxBaudrate.value, BOWDEVICE.MOTOR.id, Config.refreshConfig()).exec() }) }

            menuItemClearError0003.setOnAction { event -> doOp({ ClearErr(comboBoxComPort.value, comboBoxBaudrate.value).exec() }) }

            // bow bestand openen
            menuItemFileOpen.setOnAction { event ->
                val file = openFile(_extFilterBow)
                if (file != null) {
//                    if ((file != null) && (_file == null)) {
                    _file = file
                }
            }

            // bin bestand openen
            menuItemFileOpenBin.setOnAction { event ->
                openFile(_extFilterBin)
                }

            // hex bestand openen
            menuItemFileOpenHex.setOnAction { event ->
                openFile(_extFilterHex)
                }

            // bestand toevoegen
            menuItemFileAdd.setOnAction { event ->
                openFile(_extFilterBow, false)
                }

            val listCheckboxSrc = listOf<CheckBox>(checkBoxSrcMotor, checkBoxSrcDisplay, checkBoxSrcControlPad, checkBoxSrcBattery, checkBoxSrcTorqueSensor, checkBoxSrcPC, checkBoxSrcCharger, checkBoxSrcOther)
            val listCheckboxTgt = listOf<CheckBox>(checkBoxTgtMotor, checkBoxTgtDisplay, checkBoxTgtControlPad, checkBoxTgtBattery, checkBoxTgtTorqueSensor, checkBoxTgtPC, checkBoxTgtCharger, checkBoxTgtOther)
            val listCheckboxMsg = listOf<CheckBox>(checkBoxRequest, checkBoxResponse, checkBoxHandOff, checkBoxPing, checkBoxPong, checkBoxMsgOther)
            val listCheckboxQuality = listOf<CheckBox>(checkBoxCRCOK, checkBoxLengthOK, checkBoxCRCError, checkBoxLengthError)
            initFilter = { bSet ->
                setCheckboxStates(listCheckboxSrc, bSet)
                updateButtonText(buttonSrcClear, bSet)
                setCheckboxStates(listCheckboxTgt, bSet)
                updateButtonText(buttonTgtClear, bSet)
                setCheckboxStates(listCheckboxMsg, bSet)
                updateButtonText(buttonMsgClear, bSet)
                setCheckboxStates(listCheckboxQuality, bSet)
                updateButtonText(buttonQualityClear, bSet)
            }
            initFilter(true)
            addListenersCheckbox(listCheckboxSrc, buttonSrcClear)
            buttonSrcClear.setOnAction { _ -> OnButtonAction(listCheckboxSrc, buttonSrcClear) }
            //
            addListenersCheckbox(listCheckboxTgt, buttonTgtClear)
            buttonTgtClear.setOnAction { _ -> OnButtonAction(listCheckboxTgt, buttonTgtClear) }
            //
            addListenersCheckbox(listCheckboxMsg, buttonMsgClear)
            buttonMsgClear.setOnAction { _ -> OnButtonAction(listCheckboxMsg, buttonMsgClear) }
            //
            addListenersCheckbox(listCheckboxQuality, buttonQualityClear)
            buttonQualityClear.setOnAction { _ -> OnButtonAction(listCheckboxQuality, buttonQualityClear) }


            // TableView Kolommen
            columnAssignCellValueFactory(tableColumnMsgsId, { msg -> "${msg.id}" })
            columnAssignCellValueFactory(tableColumnMsgsTime, { msg -> "${msg.timestamp}" })
            columnAssignCellValueFactory(tableColumnMsgsRawData, { msg -> hex(msg.message) })
            columnAssignCellValueFactory(tableColumnMsgsType, { msg -> withName2(msg.type, _config.types) })
            columnAssignCellValueFactory(tableColumnMsgsSource, { msg -> withName2(msg.source, _config.devices) })
            columnAssignCellValueFactory(tableColumnMsgsTarget, { msg -> withName2(msg.target, _config.devices) })
            columnAssignCellValueFactory(tableColumnMsgsLengthCrc, { msg -> "${msg.size}" }) // /${msg.Crc}
            columnAssignCellValueFactory(tableColumnMsgsDecoded, { msg -> "${_config.decodeMessage(msg)}" })
            columnAssignCellValueFactory(tableColumnMsgsComment, { msg -> "${msg.comment}" })

        
            AnchorPane.setLeftAnchor(anchorPaneCommunication, 6.0);
            AnchorPane.setRightAnchor(anchorPaneCommunication, 6.0);
            AnchorPane.setLeftAnchor(textFieldStatus, 6.0);
            AnchorPane.setRightAnchor(textFieldStatus, 6.0);
            AnchorPane.setLeftAnchor(anchorPaneMessages, 6.0);
            AnchorPane.setRightAnchor(anchorPaneMessages, 6.0);
            AnchorPane.setTopAnchor(anchorPaneMessages, 156.0);
            AnchorPane.setBottomAnchor(anchorPaneMessages, 6.0);
            AnchorPane.setLeftAnchor(anchorPaneMessageFilter, 6.0);
            AnchorPane.setRightAnchor(anchorPaneMessageFilter, 6.0);
            AnchorPane.setLeftAnchor(tableViewMessages, 6.0);
            AnchorPane.setRightAnchor(tableViewMessages, 6.0);
            AnchorPane.setTopAnchor(tableViewMessages, 164.0);
            AnchorPane.setBottomAnchor(tableViewMessages, 6.0);

            comboBoxComPort.disableProperty().bind(isMonitoring)
            comboBoxBaudrate.disableProperty().bind(isMonitoring)
            buttonConnect.setOnAction {_ -> onButtonConnect() }
            buttonConnect.textProperty().bind(Bindings.`when`(isMonitoring).then("Disconnect").otherwise("Connect"))
            buttonLogMessages.setOnAction { _ -> isLogging.value = !isLogging.value }
            buttonLogMessages.textProperty().bind(Bindings.`when`(isLogging).then("Stop Logging").otherwise("Log Messages"))


        }


    private fun filtered(messages: ObservableList<Message>, filter: ObservableValue<Predicate<Message>>): FilteredList<Message> {
        val result = FilteredList(messages)
        result.predicateProperty().bind(filter)
        return result
    }

/*     private fun col(header: String, width: Double, formatter: (Message) -> String): TableColumn<Message, String> {
        val col = TableColumn<Message, String>(header)
        col.prefWidth = width
        col.setCellValueFactory { SimpleStringProperty(formatter.invoke(it.value)) }
        return col
    }*/
    private fun columnAssignCellValueFactory(colIn: TableColumn<Message, String>, formatter: (Message) -> String) {
        colIn.setCellValueFactory { SimpleStringProperty(formatter.invoke(it.value)) }
    }


// Logica voor knop en checkbox status
    fun updateButtonText(button: Button, bAllSelected: Boolean) {
        button.text = if (bAllSelected) "None" else "All"
    }

    fun addListenersCheckbox(checkBoxes: List<CheckBox>, button: Button) {
        checkBoxes.forEach { checkBox ->
            checkBox.selectedProperty().addListener { _, _, _ ->
                updateButtonText(button, checkBoxes.all { it.isSelected })
            }
        }
    }

    fun setCheckboxStates(checkBoxes: List<CheckBox>, bChecked: Boolean) {
        checkBoxes.forEach { checkBox ->
            checkBox.isSelected = bChecked
        }
    }


    fun OnButtonAction(checkBoxes: List<CheckBox>, button: Button) {
        val bAllSelected = checkBoxes.all { it.isSelected }
        checkBoxes.forEach { if (it.isSelected == bAllSelected) it.isSelected = !bAllSelected }
        updateButtonText(button, !bAllSelected)
    }

    fun openFile(extFilterIn: FileChooser.ExtensionFilter, bClearCurrentMessages: Boolean = true): File? {
        _fileChooser.extensionFilters.clear()
        _fileChooser.extensionFilters.add(_extFilterBin)
        _fileChooser.extensionFilters.add(_extFilterBow)
        _fileChooser.extensionFilters.add(_extFilterHex)
        _fileChooser.selectedExtensionFilter = extFilterIn
        _fileChooser.initialDirectory = java.io.File(_sDirectory)
        _fileChooser.setTitle("Open file with messages")
        val file = _fileChooser.showOpenDialog(_stage)
        if (file != null) {
            _sDirectory = file.parent
            val iFileType = when (extFilterIn) {
                _extFilterBin -> 1
                _extFilterHex -> 2
                else -> 3
            }
            if (bClearCurrentMessages)
                clearMessages()
            val config = Config.refreshConfig()
            val task = FileReaderTask(file.toPath(), iFileType, config.devices, Decoder(config), { listMessages -> Platform.runLater { addMessages(listMessages) } })
            Thread(task).start()
        }
        return file
    }   


    fun saveFile(sTitle: String, listMessages: List<Message>): File? {
        _fileChooser.extensionFilters.clear()
        _fileChooser.extensionFilters.add(_extFilterBow)
        _fileChooser.selectedExtensionFilter = _extFilterBow
        _fileChooser.initialDirectory = java.io.File(_sDirectory)
        _fileChooser.setTitle(sTitle)
        // _fileChooser.setTitle("Save messages to file")
        val file = _fileChooser.showSaveDialog(_stage)
        if (file != null) {
            _sDirectory = file.parent
            // val listMessages = tableViewMessages.selectionModel.selectedItems // selection
            // val listMessages = _messages.toList() // all messages
            // val task = WriterTask(file.toPath(), listMessages, { int, string -> Platform.runLater { showResult(int, string) } })
            // Thread(task).start()
            writeToFile(file, listMessages)
        }
        return file
    }

    fun writeToFile(file: File, listMessages: List<Message>) {
        val task = WriterTask(file.toPath(), listMessages, { int, string -> Platform.runLater { showResult(int, string) } })
        Thread(task).start()
    }

    fun showResult(iResult: Int, sMessage: String) {
        val alertType = if (iResult == 0) Alert.AlertType.INFORMATION else Alert.AlertType.ERROR
        val alert = Alert(alertType, sMessage, ButtonType.OK)
        alert.title = if (iResult == 0) "Operation successful" else "Operation failed"
        alert.showAndWait()
    }

    fun copySelectedToClipboard(messages: List<Message>) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(messages.joinToString("\n") { "${hex(it.message)}\t${it.timestamp}\t${it.id}\t${EscapeString.escape(it.comment)}" })
        clipboard.setContent(content)
    }

    fun insertMessagesAndSelect(messages: List<Message>) {
        var index: Int?
        if (tableViewMessages.selectionModel.selectedIndex >= 0) {
            index = tableViewMessages.selectionModel.selectedIndex
            _messages.addAll(index, messages)
        } else {
            index = _messages.size
            _messages.addAll(messages)
        }
        tableViewMessages.selectionModel.clearSelection()
        tableViewMessages.selectionModel.selectRange(index, index + messages.size)
    }

    fun messageFromClipboard(): List<Message> {
        val clipboard = Clipboard.getSystemClipboard()
        val content = clipboard.getContent(DataFormat.PLAIN_TEXT) as String?
        val result = mutableListOf<Message>()
        if (content != null) {
            val task = ClipboardReaderTask(content, _config.devices, Decoder(_config), { listMessages -> Platform.runLater {
                insertMessagesAndSelect(listMessages)
            }})
            Thread(task).start()
            }
        return result
    }


    fun messageDetailEdit(message : Message?) {
        val fxmlLoader = FXMLLoader(javaClass.getResource("messageDetail.fxml"))
        val vbox: VBox = fxmlLoader.load()
        val controller: MessageDetailController = fxmlLoader.getController()
        controller.showDetail(message, _stage,  vbox, handlerAddUpdate = { messageAdded, bUpdate ->
            println("Update $bUpdate Message: $messageAdded")
            if (bUpdate) {
                val index = _messages.find { it.id == messageAdded.id }?.let { _messages.indexOf(it) } ?: -1
                if (index >= 0) {
                    _messages[index] = messageAdded
                }
            } else {
                val selectedMessage = tableViewMessages.selectionModel.selectedItem
                if (selectedMessage != null) {
                    val selectedIndex = _messages.indexOf(selectedMessage)
                    _messages.add(selectedIndex + 1, messageAdded)
                } else {
                    _messages.add(messageAdded)
                }
            }
            tableViewMessages.selectionModel.select(messageAdded)
        } )
    }

    private var  _controllerPlayMessages: PlayMessagesController? = null

    fun playMessages() {
        if (_controllerPlayMessages == null)
        {
            val fxmlLoader = FXMLLoader(javaClass.getResource("playMessages.fxml"))
            _controllerPlayMessages = PlayMessagesController()
             fxmlLoader.setController(_controllerPlayMessages)
            val vbox: VBox = fxmlLoader.load()
            // _controllerPlayMessages = fxmlLoader.getController()
            try {
                println("tonen afspelen messages")
                _controllerPlayMessages!!.showDetail(_stage,  vbox,   {  tableViewMessages.selectionModel.selectedItems }, 
                                       {_controllerPlayMessages = null } )
            } catch (e: Exception) { println("$e") ; _controllerPlayMessages = null }
        }
        // else
        //     _controllerPlayMessages?.makeFrontWindow()
    }


    val isMonitoring = SimpleBooleanProperty(false)
    val isLogging  = SimpleBooleanProperty(false)

    var threadMonitor: Thread? = null
        set(value) {
            isMonitoring.set(value != null)
            if (value == null && field != null) {
                try {
                    field?.interrupt()
                    field?.join(100)
                } catch (e: InterruptedException) {
                    // ignore
                }
            }
            field = value
        }
    

    fun onButtonConnect() {    
        if (threadMonitor == null) {
            if (comboBoxComPort.value == null) {
                val alert = Alert(Alert.AlertType.ERROR, "No COM port selected", ButtonType.OK)
                alert.title = "Error"
                alert.showAndWait()
                return
            }
            if (comboBoxBaudrate.value == null) {
                val alert = Alert(Alert.AlertType.ERROR, "No baudrate selected", ButtonType.OK)
                alert.title = "Error"
                alert.showAndWait()
                return
            }
            // Start background task to read from serial port
            val task = object : Task<List<Message>>() {
                override fun call(): List<Message> {
                    val scanner = MonitorBus(comboBoxComPort.value, comboBoxBaudrate.value, _config.dataIds, { msg ->
                        // Handle new message
 //                       println("Received message2")
                        Platform.runLater {
                            textFieldStatus.text = "Received message: ${msg.id} ${msg.timestamp} msg:${hex(msg.message)} type:${withName2(msg.type, _config.types)} src:${withName2(msg.source, _config.devices)} dst:${withName2(msg.target, _config.devices)}"
                            if (isLogging.get()) {
                                _messages.add(msg)
                            }
                        }
                    })
                    return scanner.exec()
                }
            }
            task.setOnSucceeded { e ->
                val result = task.value
                threadMonitor = null
            }
/*             task.setOnFailed { e ->
                val alert = Alert(Alert.AlertType.ERROR, "Failed to read from serial port", ButtonType.OK)
                alert.title = "Error"
                alert.showAndWait()
                threadMonitor = null    
            }*/
            threadMonitor = Thread(task)
            threadMonitor?.start()
        } else {
            // Disconnect
            threadMonitor = null
            textFieldStatus.text = "Disconnected"}
    }

}