package org.bow.tool

import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.util.StringConverter
import javafx.event.ActionEvent
import javafx.beans.property.*
import javafx.beans.binding.When
import javafx.collections.ObservableList
import javafx.beans.value.ObservableBooleanValue
import kotlin.collections.copyOf
import javafx.stage.Stage
import javafx.stage.Modality
import javafx.scene.Scene
import javafx.scene.Parent
import javafx.util.Duration
import javafx.scene.control.Tooltip


@OptIn(ExperimentalUnsignedTypes::class)
class MessageDetailController {
    @FXML lateinit var buttonAdd: Button
    @FXML lateinit var buttonUpdate: Button

    @FXML lateinit var menuitemAdd: MenuItem
    @FXML lateinit var menuitemUpdate: MenuItem
    @FXML lateinit var menuitemWindowClose: MenuItem

    @FXML lateinit var comboBoxTarget: ComboBox<BowItem>
    @FXML lateinit var comboBoxStart: ComboBox<BowItem>
    @FXML lateinit var comboBoxType: ComboBox<BowItem>
    @FXML lateinit var comboBoxSource: ComboBox<BowItem>
    @FXML lateinit var comboBoxLength: ComboBox<BowItem>
    @FXML lateinit var comboBoxCommand: ComboBox<BowItem>
    @FXML lateinit var comboBoxData: ComboBox<BowItem>
    @FXML lateinit var textfieldPayload: TextField
    @FXML lateinit var checkBoxAddCRC8: CheckBox
    @FXML lateinit var textfieldCRC8: TextField
    @FXML lateinit var textfieldMessageRaw: TextField
    @FXML lateinit var textareaMessageDecoded: TextArea
    @FXML lateinit var textareaComment: TextArea
    @FXML lateinit var textfieldTimestamp: TextField

    @FXML lateinit var textfieldConvertHexData: TextField
    @FXML lateinit var textfieldConvertInt: TextField
    @FXML lateinit var textfieldConvertUInt: TextField
    @FXML lateinit var textfieldConvertFloat32: TextField
    @FXML lateinit var textfieldConvertString: TextField


    private val isMessage: SimpleBooleanProperty = SimpleBooleanProperty(false) 
    private val start: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val target: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val messageType: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val isMessageTypeKnown: SimpleBooleanProperty = SimpleBooleanProperty(false) 
    private val hasSource: SimpleBooleanProperty = SimpleBooleanProperty(false)
    private val hasCommand: SimpleBooleanProperty = SimpleBooleanProperty(false)
    private val lengthMsg: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val source: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val command: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val isGetData: SimpleBooleanProperty = SimpleBooleanProperty(false)
    private val commandData: ObjectProperty<BowItem?> = SimpleObjectProperty()
    private val payload: StringProperty = SimpleStringProperty("")
    private val calculateAddCRC: SimpleBooleanProperty = SimpleBooleanProperty(false)
    private val crc8: StringProperty = SimpleStringProperty("")
    private val messageRaw: StringProperty = SimpleStringProperty("")    
    private val timestamp: StringProperty = SimpleStringProperty("")    
    private val comment: StringProperty = SimpleStringProperty("")
    private val decoded: StringProperty = SimpleStringProperty("")

    private val hexData: StringProperty = SimpleStringProperty("")
    private val intValue: StringProperty = SimpleStringProperty("")
    private val uintValue: StringProperty = SimpleStringProperty("")
    private val float32Value: StringProperty = SimpleStringProperty("")
    private val stringValue: StringProperty = SimpleStringProperty("")



    fun <T> initComboBox(comboBox: ComboBox<T>, stringConverter: StringConverter<T>, items: ObservableList<T?>, selectedItem: ObjectProperty<T?>, isReadOnly: SimpleBooleanProperty?, handlerSelectChange: ((T?) -> Unit)? = null) {  
        comboBox.apply {
            valueProperty().bindBidirectional(selectedItem)
            setItems(items)
            isEditable = false
            setConverter(stringConverter)
            selectionModel.selectedItemProperty().addListener { _, oldValue, newValue ->
                if ((isReadOnly == null || isReadOnly.get().not()) && oldValue == null) {
                    selectedItem.value = oldValue
                }
            }
            if (isReadOnly != null) {
                styleProperty().bind(
                    When(!isReadOnly)
                        .then("-fx-opacity: 1; -fx-background-color: lightgray;")
                        .otherwise("")
                )
                //  wijzigingen doorgeven aan het model
                selectedItem.addListener { _, oldValue, newValue ->
                    println("ComboBox changed from $oldValue to $newValue")
                    if (oldValue != newValue) {
                        if (isReadOnly.value) {
                            handlerSelectChange?.invoke(newValue)
                        }
                    }
                }
            }
            if ((promptText != null) and (promptText != "")) 
            {           
                tooltip = Tooltip(promptText).apply {
                    // Optioneel: Pas de weergavetijd aan (in milliseconden)
                    showDelay = Duration.millis(300.0)
                    hideDelay = Duration.millis(1500.0)
                }           
            }
        }
    }

    fun initTextField(textField: TextInputControl, textPropertyIn: StringProperty, isReadOnly: SimpleBooleanProperty?, handlerDataChange: ((String?) -> Unit)? = null) {
        textField.apply {
            val isEditable = (isReadOnly != null)
            val isReadOnlyProp: ObservableBooleanValue = isReadOnly ?: SimpleBooleanProperty(false)
            val styleProp: StringProperty = SimpleStringProperty().apply {
                    When(isReadOnlyProp)
                    .then("-fx-opacity: 1; -fx-background-color: lightgray;")
                    .otherwise("")
            }
            textProperty().bindBidirectional(textPropertyIn)
            if (isEditable) {
                styleProperty().bindBidirectional(styleProp)
            } else {
                styleProperty().bind(styleProp)
            }
            editableProperty().bind(isReadOnlyProp)
            //
            // hier nog wijzigingen doorgeven aan het model
            textPropertyIn.addListener { _, oldValue, newValue ->
                if ((oldValue != newValue) && isReadOnlyProp.value) {
                    println("TextField ${textField.id} changed from $oldValue to $newValue")
                    handlerDataChange?.invoke(newValue)
                }
            }
        }
    }

    var conversionModel: PayloadConverter = PayloadConverter({ converterModelIn -> updateUIConverter(converterModelIn) })
    var messageModel: MessageWrapper? = null



    fun showDetail(message: Message?, stageOwner: Stage?, root: Parent,handlerAddUpdate: ((Message, Boolean) -> Unit)? = null) {

        messageModel = MessageWrapper(message, { messageModelIn -> updateUIFromModel(messageModelIn) })

        val stageDetail = Stage()
        if (stageOwner != null) {
            stageDetail.initOwner(stageOwner)
            stageDetail.initModality(Modality.NONE)
        }
        if (message == null) {
            buttonUpdate.isDisable = true
            stageDetail.title = "New Message"
        } else {
            stageDetail.title = "Edit Message " + messageModel!!.id
        }
        stageDetail.scene = Scene(root)



        val listDevices = Config.getInstance().devicesList
        val listTypes = Config.getInstance().typesList
        val listCommands = Config.getInstance().commandsList
        val listDataIds = Config.getInstance().dataIdsList


        val converter = object : StringConverter<BowItem>() {
            override fun toString(item: BowItem?): String? {
                return if (item == null) "-" else "0x" + item.id.toString(16).uppercase() + " " + item.Name
            }
            override fun fromString(string: String?): BowItem? {
                return null
            }
        }

        initComboBox<BowItem>(comboBoxStart, converter, Config.getInstance().startsList, start, SimpleBooleanProperty(true), { newValue -> messageModel?.alterStart(newValue) })
        initComboBox<BowItem>(comboBoxTarget, converter, listDevices, target, isMessage, { newValue -> messageModel?.alterTarget(newValue) })
        initComboBox<BowItem>(comboBoxType, converter, listTypes, messageType, isMessage, { newValue -> messageModel?.alterType(newValue) })
        initComboBox<BowItem>(comboBoxSource, converter, listDevices, source, hasSource, { newValue -> messageModel?.alterSource(newValue) })
        initComboBox<BowItem>(comboBoxLength, converter, Config.getInstance().lengthsList, lengthMsg, hasSource, { newValue -> messageModel?.alterLength(newValue) })
        initComboBox<BowItem>(comboBoxCommand, converter, listCommands, command, hasCommand, { newValue -> messageModel?.alterCommand(newValue) })
        initComboBox<BowItem>(comboBoxData, converter, listDataIds, commandData, isGetData, { newValue -> messageModel?.alterData(newValue) })

        // textfieldPayload.apply { 
        //     textProperty().bindBidirectional(payload);
        //     //
        //     styleProperty().bind(
        //         When(isMessage)
        //             .then("")
        //             .otherwise("-fx-opacity: 1; -fx-background-color: lightgray;")
        //     )
        //     editableProperty().bind(isMessage);
        //  }
        textfieldCRC8.apply { textProperty().bind(crc8) }
        textfieldMessageRaw.apply { textProperty().bind(messageRaw) }
        textareaMessageDecoded.apply { textProperty().bind(decoded) }
        initTextField(textfieldPayload, payload, SimpleBooleanProperty(true), { newValue -> messageModel?.alterPayload(newValue) })
        initTextField(textfieldTimestamp, timestamp, SimpleBooleanProperty(true), { newValue -> messageModel?.alterTimestamp(newValue) })
        initTextField(textareaComment, comment, SimpleBooleanProperty(true), { newValue -> messageModel?.alterComment(newValue) })
        // textfieldTimestamp.apply { textProperty().bindBidirectional(timestamp) }
        // textareaComment.apply { textProperty().bindBidirectional(comment) }


        checkBoxAddCRC8.apply {
            selectedProperty().bindBidirectional(calculateAddCRC)

            calculateAddCRC.addListener { _, oldValue: Boolean, newValue: Boolean ->
                println("CheckBox Add CRC8 changed from $oldValue to $newValue")
                if (oldValue != newValue) {
                    messageModel?.alterCalculateAddCRC(newValue)
                }
            }
        }

        initTextField(textfieldConvertHexData, hexData, SimpleBooleanProperty(true), { newValue -> conversionModel.alterHexData(newValue) })
        initTextField(textfieldConvertInt, intValue, SimpleBooleanProperty(true), { newValue -> conversionModel.alterIntValue(newValue) })
        initTextField(textfieldConvertUInt, uintValue, SimpleBooleanProperty(true), { newValue -> conversionModel.alterUIntValue(newValue) })
        initTextField(textfieldConvertFloat32, float32Value, SimpleBooleanProperty(true), { newValue -> conversionModel.alterFloat32Value(newValue) })
        initTextField(textfieldConvertString, stringValue, SimpleBooleanProperty(true), { newValue -> conversionModel.alterStringValue(newValue) })

        fun updateSaveMessage(bUpdate: Boolean) {
            println(if (bUpdate) "Update message" else "Save message")
            val newMessage = messageModel?.buildMessage(bUpdate)
            if (newMessage != null) {
                handlerAddUpdate?.invoke(newMessage, bUpdate)
                if (bUpdate)
                    messageModel?.id = newMessage.id
            } else {
                val alert = Alert(Alert.AlertType.ERROR)
                alert.title = "Error"
                alert.headerText = "Invalid Message"
                alert.contentText = "The message could not be created from the provided data."
                alert.showAndWait()
            }
        }

        menuitemWindowClose.setOnAction { _ -> stageDetail.close() }
        menuitemUpdate.setOnAction { event: ActionEvent -> updateSaveMessage(true) }
        menuitemAdd.setOnAction { event: ActionEvent -> updateSaveMessage(false) }
        buttonUpdate.onAction = menuitemUpdate.onAction
        buttonAdd.onAction = menuitemAdd.onAction
        // buttonAdd.setOnAction { event ->  menuitemAdd.onAction?.value?.handle(event) }
        // buttonUpdate.setOnAction { event -> menuitemAdd.onAction?.value?.handle(event) }


        // source.addListener { _, _, newValue ->
        //     if (!hasCommand.value && newValue != null) { source.set(null) }
        // }
        // comboBoxSource.apply {
        //     valueProperty().bindBidirectional(source);
        //     selectionModel.selectedItemProperty().addListener { _, _, newValue ->
        //         source.value = newValue
        //     }
        //     //
        //     setConverter(comboBoxTarget.converter)
        //     //
        //     //showingProperty().bindBidirectional(hasCommand)
        //     //
        //     styleProperty().bind(
        //         When(hasCommand)
        //             .then("")
        //             .otherwise("-fx-opacity: 1; -fx-background-color: lightgray;")
        //     )
        //     //
        //     setItems(devices)
        //     //
        //     // onChanged { value -> messageModel?.source = value }
        // }


        //  comboBoxTarget.setOnShowing{ event ->
        //         val current = comboBoxTarget.value
        //         val devices = observableArrayList(Config.getInstance().devices.items.values.toList().sortedBy { it.id })
        //         devices.add(0, null)
        //         println("Devices: $devices")        
        //         comboBoxTarget.setItems(devices)
        //         if (current != null) {
        //             val match = comboBoxTarget.items.find { it == current }
        //             if (match != null) {
        //                 comboBoxTarget.value = match
        //             } else {
        //                 comboBoxTarget.value = null
        //             }
        //         }
        //     }
        // val devices = observableArrayList(Config.getInstance().devices.items.values.toList().sortedBy { it.id })
        // devices.add(0, null)
        // comboBoxTarget.setItems(devices)
        stageDetail.show()
    }


    fun updateUIFromModel(messageModelIn: MessageWrapper) {
        println("Updating UI from model: ${messageModelIn}")
        start.value = Config.getInstance().starts.findById((messageModelIn.start?.id?.toInt() ?: -1))
        isMessage.value = messageModelIn.isMessage
        hasSource.value = messageModelIn.hasSource
        hasCommand.value = messageModelIn.hasCommand
        isGetData.value = messageModelIn.isGetData
        //
        messageType.value = messageModelIn.messageType
        println("Target ui update: ${messageModelIn.target}")
        target.value = messageModelIn.target
        println("Source ui update: ${messageModelIn.source}")
        source.value = messageModelIn.source
        lengthMsg.value = messageModelIn.lengthMsg
        command.value = messageModelIn.command
        commandData.value = messageModelIn.data
        payload.value = messageModelIn.payload.toHexString().chunked(4).joinToString(" ")
        calculateAddCRC.value = messageModelIn.addCRC8
        crc8.value = if (messageModelIn.crc8Calc != null) hex(messageModelIn.crc8Calc!!) else ""
        // messageRaw.value = messageModelIn.messageRaw.toHexString().chunked(4).joinToString(" ")
        messageRaw.value = messageModelIn.getEscapedMessageRaw().toHexString().chunked(4).joinToString(" ")
        timestamp.value = messageModelIn.timestamp?.toString() ?: ""
        comment.value = messageModelIn.comment ?: ""
        decoded.value = messageModelIn.decoded ?: ""
        println("Timestamp: ${messageModelIn.timestamp}")
        println("Comment: ${messageModelIn.comment}")

        // override fun toString(): String {
        //     return "Message(id=${messageModel.id}, type=${messageModel.type}, target=${messageModel.target}, source=${messageModel.source}, cmd=${messageModel.command})"
        // }

    }

    fun updateUIConverter(converterModelIn: PayloadConverter) {
        hexData.value = converterModelIn.hexData
        intValue.value = converterModelIn.intValue
        uintValue.value = converterModelIn.uintValue
        float32Value.value = converterModelIn.float32Value
        stringValue.value = converterModelIn.stringValue
    }    
}



@kotlin.ExperimentalUnsignedTypes
class MessageWrapper(messageIn: Message?, val updateUI: (MessageWrapper) -> Unit) {

    var id: Int? = messageIn?.id ?: 0
    var start: BowItem? = null
    var target: BowItem? = null
    var messageType: BowItem? = null
    var isMessage: Boolean = false
    var hasSource: Boolean = false
    var hasCommand: Boolean = false
    var source: BowItem? = null
    var lengthMsg: BowItem? = null
    var command: BowItem? = null
    var isGetData: Boolean = false
    var data: BowItem? = null
    var payload: UByteArray = ubyteArrayOf()
    var addCRC8: Boolean = false
    var crc8Calc: UByte? = null
    var timestamp: Long? = messageIn?.timestamp ?: System.currentTimeMillis()
    var comment: String? = messageIn?.comment ?: ""
    var decoded: String? = if (messageIn != null) Config.getInstance().decodeMessage(messageIn) else ""
    private var bUpdating = false
    var determineAddCRC8: Boolean = true
    var payloadStartIndex: Int = 0
    var payloadEndIndex: Int = 0
    var decoder = Decoder(Config.getInstance())
    var messageRaw: UByteArray = messageIn?.message?.copyOf() ?: ubyteArrayOf()  // messageIn.message is not 0x10 escaped ??????
        set(value) {
            if (addCRC8 && (value.size > 1)) {
                val escapedValueWithoutCRC = ubyteArrayOf(value[0]) + escape0x10Bytes(value.sliceArray(1 until value.size - 1))
                // val escapedValueWithoutCRC = escape0x10Bytes(value, true)
                crc8Calc = CRC8.crc8Array(escapedValueWithoutCRC)
                value[value.size - 1] = crc8Calc!!
            }
            else
                crc8Calc = null
            field = value
            parseMessageRaw()
            //decoded = decoder.decode(buildMessage(true))
            updateView()
        }
    

    init {
         parseMessageRaw()
         determineAddCRC8 = false
         updateView()
    }

    fun buildMessage(bUpdate: Boolean): Message? {
        var Result = Message(
            type = messageType?.id?.toUByte() ?: 0u,
            target = target?.id?.toUByte() ?: 0u,
            source = if (hasSource) source?.id?.toUByte() else null,
            size = messageRaw?.size?.toUByte() ?: 0u,
            message = messageRaw.copyOf(), // Message.message (raw data) is not 0x10 escaped ??????
            previous = null
            )
            if (timestamp != null)
                Result.timestamp = timestamp!!
            Result.comment = comment ?: ""
            if (bUpdate)
                Result.id = this.id ?: Result.id
            return Result
    }

    fun parseMessageRaw() {
        start = null
        target = null
        messageType = null
        isMessage = false
        hasSource = false
        hasCommand = false
        source = null
        lengthMsg = null
        command = null
        isGetData = false
        data = null
        payload = ubyteArrayOf()
        //validCRC8 = false
        //crc8Calc = null
        //decoded = null

        val iMessageSize = messageRaw.size
        payloadStartIndex = iMessageSize
        payloadEndIndex = iMessageSize
        if (iMessageSize > 0) {
            start = Config.getInstance().starts.findById(messageRaw[0].toInt())
            isMessage = (start != null) && (start!!.id == BOWSTART.MESSAGE.id)
            if (iMessageSize >= 2) {
                //
                target = Config.getInstance().devicesList.findById(messageRaw[1].toInt().shr(4))
                println("Target ui update: ${target}")
                val messageId = messageRaw[1].and(0x0Fu)
                messageType = Config.getInstance().typesList.findById(messageRaw[1].toInt() and 0xF)
                var iOffset = 2
                hasCommand = messageId in listOf(BOWTYPE.REQUEST.id, BOWTYPE.RESPONSE.id, BOWTYPE.UNKNOWN.id)
                hasSource = hasCommand || (messageId == BOWTYPE.PONG.id || messageId == BOWTYPE.PING.id)
                if (hasSource) {
                    val iLengthMsg: Int = if (messageRaw.size >= 3) (messageRaw[2].and(0xFu)).toInt() else -1
                    val sourceId: Int = if (messageRaw.size >= 3) (messageRaw[2].toInt().ushr(4)) else -1
                    source = Config.getInstance().devicesList.findById(sourceId)
                    lengthMsg = Config.getInstance().lengthsList.find { if (it != null) (it.id.toInt() == iLengthMsg) else (iLengthMsg == -1) }
                    iOffset ++
                    if (hasCommand) {
                        val cmdId: Int = if (messageRaw.size >= 4) messageRaw[3].toInt() else -1
                        command = Config.getInstance().commandsList.findById(cmdId)
                        iOffset ++
                        val extraData: Int = if (messageRaw.size >= 5) messageRaw[4].toInt() else -1
                        iOffset ++
                        if (messageId == BOWTYPE.RESPONSE.id)
                        {
                            val responseData: Int = if (messageRaw.size >= 6) messageRaw[5].toInt() else -1
                            iOffset ++
                        }
                        isGetData = (cmdId == BOWCOMMAND.GET_DATA.id.toInt())
                        if (isGetData) {
                            val dataId: Int = if (messageRaw.size > iOffset) messageRaw[iOffset].toInt() else -1 // iOffset is 5 or 6
                            data = Config.getInstance().dataIdsList.findById(dataId)
                            iOffset ++
                        }
                        iOffset = 4 // make data part of the payload
                    }
                }
                //
                if (determineAddCRC8)
                {
                    crc8Calc = CRC8.crc8Array(ubyteArrayOf(messageRaw[0]) + escape0x10Bytes(messageRaw.sliceArray(1 until messageRaw.size - 1)))
                    // crc8Calc = CRC8.crc8Array(escape0x10Bytes(messageRaw, true))
                    addCRC8 = crc8Calc == messageRaw[messageRaw.size - 1]
                }
                if (!addCRC8)
                    crc8Calc = CRC8.crc8Array(ubyteArrayOf(messageRaw[0]) + escape0x10Bytes(messageRaw.sliceArray(1 until messageRaw.size)))
                    // crc8Calc = CRC8.crc8Array(escape0x10Bytes(messageRaw, false))
                //
                if (iMessageSize >= iOffset + 1) {
                    payloadStartIndex = iOffset
                    payloadEndIndex = iMessageSize - (if (addCRC8) 1 else 0) // Exclude CRC8
                    payload = messageRaw.sliceArray(payloadStartIndex until payloadEndIndex)
                }
            }
        }
    }


    fun updateView() {
        if (!bUpdating)
        {
            bUpdating = true
            try {
                updateUI(this)
            }
            finally {
                bUpdating = false
            }
        }
    }


    fun minLengthMessageRaw(minLength: Int): UByteArray {
        if (messageRaw.size < minLength) {
            val newArray = UByteArray(minLength)
            for (i in messageRaw.indices) {
                newArray[i] = messageRaw[i]
            }
            return newArray
        }
        return messageRaw
    }

    fun unescape0x10Bytes(input: UByteArray): UByteArray {
        val output = mutableListOf<UByte>()
        var bEscaped: Boolean = false
        for (byte in input) {
        // val lengthLimit = input.size - (if (bHasCRC8) 1 else 0) 
        // for (iT1 in 1 until lengthLimit) {
        //     val byte: UByte = input[iT1]
            if (byte == 0x10u.toUByte()) {
                if (!bEscaped) {
                    output.add(0x10u)
                    bEscaped = true
                }
                else // skip this byte
                    bEscaped = false
            } else {
                output.add(byte)
                bEscaped = false
            }
        }
        // if (bHasCRC8)
        //     output.add(input[input.size - 1])
        return output.toUByteArray()
    }

    fun escape0x10Bytes(input: UByteArray): UByteArray {
        val output = mutableListOf<UByte>()
        for (byte in input) {
        // val lengthLimit = input.size - (if (bHasCRC8) 1 else 0) 
        // for (iT1 in 1 until lengthLimit) {
        //     val byte: UByte = input[iT1]
            if (byte == 0x10u.toUByte()) {
                output.add(0x10u)
                output.add(0x10u)
            } else {
                output.add(byte)
            }
        }
        // if (bHasCRC8)
        //     output.add(input[input.size - 1])
        return output.toUByteArray()
    }
    fun getEscapedMessageRaw(): UByteArray {
        // return escape0x10Bytes(messageRaw, addCRC8)
        val output = mutableListOf<UByte>()
        if (messageRaw.size > 0) {
            output.add(messageRaw[0])
            output.addAll(escape0x10Bytes(messageRaw.sliceArray(1 until messageRaw.size - (if (addCRC8) 1 else 0))).toList())
            if (addCRC8)
                output.add(messageRaw[messageRaw.size - 1])
        }
        return output.toUByteArray()
    }

    fun recalculateCRC8() {
        if (addCRC8) {
            val crcValue = CRC8.crc8Array(messageRaw.sliceArray(0 until messageRaw.size - 1))
            messageRaw[messageRaw.size - 1] = crcValue
        }
    }

    fun alterStart(bowitemIn: BowItem?) {
        val messageRawSet: UByteArray = minLengthMessageRaw(1)
        messageRawSet[0] = bowitemIn?.id ?: 0u
        recalculateCRC8()
        messageRaw = messageRawSet
    }
    fun alterTarget(bowitemIn: BowItem?) {
        println("Altering target to: $bowitemIn")
        val messageRawSet: UByteArray = minLengthMessageRaw(2)
        val id = bowitemIn?.id?.toInt() ?: 0
        messageRawSet[1] = (messageRawSet[1].and(0x0Fu).or((id shl 4).toUByte()))
        println("Target ubyte: ${messageRawSet[1]}")
        messageRaw = messageRawSet
    }
    fun alterType(bowitemIn: BowItem?) {
        val messageRawSet: UByteArray = minLengthMessageRaw(2)
        val id: UByte = bowitemIn?.id?.toUByte() ?: 0u
        messageRawSet[1] = messageRawSet[1].and(0xF0u).or(id)
        messageRaw = messageRawSet
    }
    fun alterSource(bowitemIn: BowItem?) {
        val messageRawSet: UByteArray = minLengthMessageRaw(3)
        val id = bowitemIn?.id?.toInt() ?: 0
        messageRawSet[2] = (messageRawSet[2].and(0x0Fu).or((id shl 4).toUByte()))
        messageRaw = messageRawSet
    }
    fun alterLength(bowitemIn: BowItem?) {
        val messageRawSet: UByteArray = minLengthMessageRaw(3)
        val id: UByte = bowitemIn?.id?.toUByte() ?: 0u
        messageRawSet[2] = messageRawSet[2].and(0xF0u).or(id)
        messageRaw = messageRawSet
    }
    fun alterCommand(bowitemIn: BowItem?) {
        val messageRawSet: UByteArray = minLengthMessageRaw(4)
        messageRawSet[3] = bowitemIn?.id ?: 0u
        messageRaw = messageRawSet
    }
    fun alterData(bowitemIn: BowItem?) {
        val iIndex = 5 + (if ((messageRaw[1].toInt() and 0xF) in listOf<Int>(BOWTYPE.RESPONSE.id.toInt())) 1 else 0)
        val messageRawSet: UByteArray = minLengthMessageRaw(iIndex+1)
        messageRawSet[iIndex] = bowitemIn?.id ?: 0u
        messageRaw = messageRawSet
    }
    fun alterCalculateAddCRC(booleanIn: Boolean) {
        println("CheckBox Add CRC8 changed to $booleanIn")
        if (booleanIn) {
            val newArray = minLengthMessageRaw(messageRaw.size+1)
            newArray[newArray.size - 1] = CRC8.crc8Array(messageRaw)
            addCRC8 = true
            messageRaw = newArray
        }
        else {
            addCRC8 = false
//            if ((messageRaw.size > 2) && (CRC8.crc8Array(messageRaw.sliceArray(0..messageRaw.size - 2)) == messageRaw[messageRaw.size -1])) {
                messageRaw = messageRaw.sliceArray(0..messageRaw.size -2)
//            }
        }        
    }
    fun alterPayload(sHex: String?)
    {
        val listElementsHelp = hexStringToListUByte((sHex ?: "").uppercase().replace(Regex("[^0-9A-F]"), ""))
        // vergelijk met huidige payload
        if (!payload.contentEquals(listElementsHelp.toUByteArray())) 
        {
            val iLengthMsg = payloadStartIndex + listElementsHelp.size + (if (addCRC8) 1 else 0)
            val messageRawSet: UByteArray = minLengthMessageRaw(iLengthMsg).sliceArray(0 until iLengthMsg)
            for (i in listElementsHelp.indices) {
                messageRawSet[payloadStartIndex + i] = listElementsHelp[i]
            }
            messageRaw = messageRawSet
        }
    }
    fun alterTimestamp(sTimestamp: String?)
    {
        if (sTimestamp != null)
        {
            val longTimestamp: Long? = sTimestamp.toLongOrNull()
            if (longTimestamp != null)
                timestamp = longTimestamp
        }
    }
    fun alterComment(sCommentIn: String?)
    {
        comment = sCommentIn
    }
}

fun ListUBytetoHexString(listIn: List<UByte>): String {
    return listIn.joinToString(" ") { byte -> String.format("%02X", byte)
    }
}

fun hexStringToListUByte(sIn: String): List<UByte> {
    val result = ArrayList<UByte>()
    var sProcess: String = sIn; 
    if (sIn.length.and(1) == 1)
        sProcess = sIn.dropLast(1)
    result.addAll(sProcess.chunked(2).map { it.toInt(16).toUByte() })
    return result
}

fun ListUBytetoIntString(listIn: List<UByte>): String {
    var intValue: Int = 0
    for (i in listIn.indices) {
        intValue = (intValue shl 8) or listIn[i].toInt()
    }
    return intValue.toString()
}

fun ListUBytetoUIntString(listIn: List<UByte>): String {
    var uintValue: UInt = 0u
    for (i in listIn.indices) {
        uintValue = (uintValue shl 8) or listIn[i].toUInt()
    }
    return uintValue.toString()
}

fun ListUBytetoString(listIn: List<UByte>): String {
    val charList = listIn.map { it.toInt().toChar() }
    println("Char list: $charList")
    return charList.joinToString("")
}

class PayloadConverter(val updateUI: (PayloadConverter) -> Unit) {
    private var iMode: Int = 0
    private var listElements: List<UByte> = listOf() 
    private var bUpdating = false
    //
    var hexData: String = ""
    var intValue: String = ""
    var uintValue: String = ""
    var float32Value: String = ""
    var stringValue: String = ""

    private fun convert(listElementsIn: List<UByte>, iMode: Int) {
        if (!bUpdating)
        {
            listElements = listElementsIn
            if (iMode != 5) { stringValue = ListUBytetoString(listElements) }
            if (iMode != 1) { hexData = hex(listElements).chunked(4).joinToString(" ") }
            if (iMode != 2) { intValue = if (listElements.size > 0) asInt(listElements) else "" }
            if (iMode != 3) { uintValue = if (listElements.size > 0) asUint(listElements) else "" }
            if (iMode != 4) { float32Value = if (listElements.size > 0) asFloat32(listElements) else "" }
            //
            bUpdating = true
            try { 
                updateUI(this)
            }
            finally { 
                bUpdating = false
            }
        }
    }


    fun alterHexData(value: String?) {
        hexData = value ?: ""
        val listElementsHelp = hexStringToListUByte((value ?: "").uppercase().replace(Regex("[^0-9A-F]"), ""))
        convert(listElementsHelp, 1)
    }
    fun alterIntValue(value: String?) {
        intValue = value ?: ""
        val intVal = value?.toIntOrNull() ?: 0
        val listElementsHelp = listOf(
            ((intVal shr 24) and 0xFF).toUByte(),
            ((intVal shr 16) and 0xFF).toUByte(),
            ((intVal shr 8) and 0xFF).toUByte(),
            (intVal and 0xFF).toUByte()
        )
        convert(listElementsHelp, 2)
    }
    fun alterUIntValue(value: String?) {
        uintValue = value ?: ""
        val uintVal = value?.toUIntOrNull() ?: 0u
        val listElementsHelp = listOf(
            ((uintVal.toInt() shr 24) and 0xFF).toUByte(),
            ((uintVal.toInt() shr 16) and 0xFF).toUByte(),
            ((uintVal.toInt() shr 8) and 0xFF).toUByte(),
            (uintVal.toInt() and 0xFF).toUByte()
        )       
        convert(listElementsHelp, 3)
    }
    fun alterFloat32Value(value: String?) {
        float32Value = value ?: ""
        val floatVal = value?.toFloatOrNull() ?: 0.0f
        val bits = floatVal.toBits()
        val listElementsHelp = listOf(
            ((bits shr 24) and 0xFF).toUByte(),
            ((bits shr 16) and 0xFF).toUByte(),
            ((bits shr 8) and 0xFF).toUByte(),
            (bits and 0xFF).toUByte()
        )       
        convert(listElementsHelp, 4)
    }
    fun alterStringValue(value: String?) {
        stringValue = value ?: ""
        val listElementsHelp = ArrayList<UByte>()
        for (char in (value ?: "")) {
            val charCode = char.code
            listElementsHelp.add(charCode.toUByte())
        }
        convert(listElementsHelp, 5)
    }
}