package org.bow.tool

import com.fazecast.jSerialComm.SerialPort

@OptIn(ExperimentalUnsignedTypes::class)
open class SerialOp(val serialPort: SerialPort, private val baudRate: Int) {

    protected fun open(): Boolean {
        serialPort.setBaudRate(baudRate)
        println("Trying to open serial port ${serialPort.systemPortName}(${serialPort.descriptivePortName}) at ${baudRate} baud")
        if (!serialPort.isOpen() && !serialPort.openPort()) {
            println("Failed to open port")
            return false
        }
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 225, 0)
        println("Port open")
        return true
    }

    protected fun read(buffer: ByteArray): Int {
        return serialPort.readBytes(buffer, buffer.size)
    }

    protected fun sendGetData(target: UByte, type: UByte, id: UByte) {
        sendCmd(target, BOWCOMMAND.GET_DATA.id, type, id)
    }

    protected fun sendGetDataArray(target: UByte, type: UByte, id: UByte, offset: UByte) {
        sendCmd(target, BOWCOMMAND.GET_DATA.id, type, id, offset)
    }

    protected fun sendPutData(target: UByte, type: UByte, id: UByte, vararg data: UByte) {
        sendCmd(target, BOWCOMMAND.PUT_DATA.id, type, id, *data)
    }

    protected fun sendPutDataArray(target: UByte, type: UByte, id: UByte, from: UByte, to: UByte, length: UByte, vararg data: UByte) {
        sendCmd(target, BOWCOMMAND.PUT_DATA.id, type, id, from, to, length, *data)
    }

    protected fun sendWakeUp(target: UByte) {
        sendCmd(target, BOWCOMMAND.WAKE_UP_BATTERY.id)
    }

    protected fun sendGetDisplaySerial() {
        sendCmd(BOWDEVICE.DISPLAY.id, BOWCOMMAND.GET_DISPLAY_SERIAL.id)
    }

    protected fun sendGetDisplaySerialFromMotor(index: Int) {
        val id: UByte = when (index) {
            0 -> BOWDATA.PAIRED_SERIAL1.id
            1 -> BOWDATA.PAIRED_SERIAL2.id
            else -> throw IllegalArgumentException("Index ${index} out of range")
        }
        sendGetDataArray(BOWDEVICE.MOTOR.id, 0x40u, id, 0x00u)
    }

    protected fun sendStoreDisplaySerialInMotor(index: Int, vararg serial: UByte) {
        val id: UByte = when (index) {
            0 -> BOWDATA.PAIRED_SERIAL1.id
            1 -> BOWDATA.PAIRED_SERIAL2.id
            else -> throw IllegalArgumentException("Index ${index} out of range")
        }
        sendPutDataArray(BOWDEVICE.MOTOR.id, 0x40u, id, 0x00u, serial.size.toUByte(), serial.size.toUByte(), *serial)
    }

    protected fun sendGetMotorSerialFromMotor() {
        sendGetDataArray(BOWDEVICE.MOTOR.id, 0x70u, 0xd1u, 0x00u)
    }

    protected fun sendGetMotorSerialFromBattery() {
        sendGetDataArray(BOWDEVICE.BATTERY.id, 0x70u, 0xc8u, 0x00u)
    }

    protected fun sendStoreMotorSerialInBattery(vararg serial: UByte) {
        sendPutDataArray(BOWDEVICE.BATTERY.id, 0x70u, 0xc8u, 0x00u, serial.size.toUByte(), serial.size.toUByte(), *serial)
    }

    protected fun sendStoreDisableServiceCounter() {
        sendPutData(BOWDEVICE.BATTERY.id, 0x08u, 0x3bu, 0x00u, 0x00u, 0x00u, 0x00u)
    }

    protected fun sendStoreClearErrorState() {
        sendPutData(BOWDEVICE.BATTERY.id, 0x00u, 0x29u, 0x00u)
    }

    protected fun sendGetErrorState() {
        sendGetData(BOWDEVICE.BATTERY.id, 0x00u, 0x29u)
    }

    /**
     * Send a single '0' byte, which should wake up the battery.
     */
    protected fun sendWakeUpByte() {
        sendRaw(0x00u)
    }

    /**
     * Sends a PONG message to the given target.
     */
    protected fun sendPong(target: UByte) {
        send(byte1(target, 0x03u), byte2(BOWDEVICE.PC.id, 0x00))
    }

    /**
     * Send the given command to the given target, with the given (optional) data.
     */
    protected fun sendCmd(target: UByte, cmd: UByte, vararg data: UByte) {
        send(byte1(target, 0x01u), byte2(BOWDEVICE.PC.id, data.size), cmd, *data)
    }

    /**
     * Send the given command, after escaping it and adding a 0x10 start byte and CRC.
     */
    private fun send(vararg cmd: UByte) {
        val outList = cmd.toMutableList()
        outList.add(0, 0x10u)
        outList.add(CRC8.crc8Bow(outList))

        sendRaw(outList.flatMapIndexed { ind, it -> if (ind != 0 && it.toUInt() == 0x10u) listOf(it, it) else listOf(it) })
    }

    /**
     * Send the given list of bytes directly to the serial port.
     */
    private fun sendRaw(data: List<UByte>) {
        sendRaw(*data.toUByteArray())
    }

    /**
     * Send the given bytes directly to the serial port.
     */
    private fun sendRaw(vararg data: UByte) {
        serialPort.writeBytes(data.toByteArray(), data.size)
    }

    /**
     * Constructs the second byte (index 1) of a BOW message, based on the given target and type.
     */
    private fun byte1(target: UByte, type: UByte): UByte {
        return (target.toUInt().shl(4) or type.toUInt()).toUByte()
    }

    /**
     * Constructs the third byte (index 2) of a BOW message, based on the given source and data size.
     */
    private fun byte2(source: UByte, dataSize: Int): UByte {
        return (source.toUInt().shl(4) or dataSize.toUInt()).toUByte()
    }
}