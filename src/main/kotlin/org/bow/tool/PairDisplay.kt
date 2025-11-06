package org.bow.tool

import com.fazecast.jSerialComm.SerialPort

object PairDisplay {
    @JvmStatic
    fun main(args: Array<String>) {
        val comPort = SerialPort.getCommPorts()[0]
        DisplayPairer(comPort, 19200, 1).exec()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class DisplayPairer(serialPort: SerialPort, baudRate: Int, private var index: Int) : StdLoop(serialPort, baudRate) {
    private enum class State {
        GET_DISPLAY_SERIAL,
        GET_STORED_SERIAL,
        PUT_SERIAL,
        CHECK_STORED_SERIAL
    }

    private var state = State.GET_DISPLAY_SERIAL
    private var displaySerial: List<UByte> = emptyList()

    fun exec(): List<Message> {
        if (!open()) return emptyList()

        loop(Mode.CHECK_BAT)
        return getMessageLog()
    }

    override fun sendCommand() {
        when (state) {
            State.GET_DISPLAY_SERIAL -> sendGetDisplaySerial()
            State.GET_STORED_SERIAL -> sendGetDisplaySerialFromMotor(index)
            State.PUT_SERIAL -> sendStoreDisplaySerialInMotor(index, *displaySerial.toUByteArray())
            State.CHECK_STORED_SERIAL -> sendGetDisplaySerialFromMotor(index)
        }
    }

    override fun handleResponse(message: Message): Result {
        if (message.tgt() != BOWDEVICE.PC.id || !message.isRsp()) {
            return Result.CONTINUE
        }

        when (state) {
            State.GET_DISPLAY_SERIAL -> {
                if (message.src() == BOWDEVICE.DISPLAY.id && message.isCmd(BOWCOMMAND.GET_DISPLAY_SERIAL.id)) {
                    displaySerial = message.data()
                    log("Display serial: ${hex(displaySerial)}")
                    state = State.GET_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.GET_STORED_SERIAL -> {
                if (message.src() == BOWDEVICE.MOTOR.id && message.isCmd(BOWCOMMAND.GET_DATA.id)) {
                    val motorDisplaySerial = message.data().drop(4)
                    log("Display serial stored in motor slot ${index + 1}: ${hex(motorDisplaySerial)}")
                    if (displaySerial.equals(motorDisplaySerial)) {
                        log("Serials already match, no change made")
                        return Result.DONE
                    }
                    state = State.PUT_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.PUT_SERIAL -> {
                if (message.src() == BOWDEVICE.MOTOR.id && message.isCmd(BOWCOMMAND.PUT_DATA.id)) {
                    log("New display serial stored in motor!")
                    state = State.CHECK_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.CHECK_STORED_SERIAL -> {
                if (message.src() == BOWDEVICE.MOTOR.id && message.isCmd(BOWCOMMAND.GET_DATA.id)) {
                    log("Display serial stored in motor slot ${index + 1}: ${hex(message.data().drop(4))}")
                    return Result.DONE
                }
            }
        }
        return Result.CONTINUE
    }

}