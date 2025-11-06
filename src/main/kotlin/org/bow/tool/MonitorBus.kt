package org.bow.tool

import com.fazecast.jSerialComm.SerialPort
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


@OptIn(ExperimentalUnsignedTypes::class)
class MonitorBus(serialPort: SerialPort, baudRate: Int, dataIdsByInt: BowItems, val newMessageHandler: (message: Message) -> Unit) : SerialOp(serialPort, baudRate) {

    private val toScan = List(256) { it.toUByte() }.toMutableList()
    private val allTypes = (0x00u..0xffu).map { it.toUByte() }
    private val types = listOf<UByte>(0x00u, 0x04u, 0x08u, 0x14u, 0x28u, 0x70u, 0x40u, 0x44u, 0x48u, 0x54u) + allTypes

    private var first = true
    private var idPos = 0
    private var typePos = 0
    private var request: Message? = null
    private val results = ArrayList<Pair<Message, Message>>()
    private val decoder = GetDataDecoder(dataIdsByInt)
    private var arrOffset = 0u



    private enum class State {
        // Clear everything currently in the buffer, to get rid of noise
        FLUSH,

        // Wait for the target device to wake up
        WAIT_FOR_BAT,

        // Monitor the bus
        MONITOR_BUS,

        // Wait for a response to a command we sent
        WAIT_RESPONSE,

        // Everything is done, we'll exit the loop
        DONE
    }

    enum class Mode {
        // In this mode, we keep trying to wake the battery up.
        WAKEUP_BAT,

        // In this mode, we try to wake the battery, but go to direct mode if it doesn't respond timely.
        CHECK_BAT,

        // In this mode, we don't try to wake the battery, and drive communication ourselves.
        DIRECT
    }

    enum class Result {
        // Result of handling the response is: continue in the same state.
        CONTINUE,

        // Result of handling the response is: the next command should be sent.
        SEND_COMMAND,

        // Result of handling the response is: we are done.
        DONE
    }

    private val stdoutQueue = LinkedBlockingQueue<String>()
    private val messageLog = ArrayList<Message>(500);
    private var state = State.FLUSH
    private val readBuffer = ByteArray(1024)
    private val parser = MessageParser(this::handleMessage) { message -> log("Incomplete message: ${hex(message)}, crc:${hex(CRC8.crc8Bow(message.dropLast(1)))}") }
    private var waited = 0




    fun exec(): List<Message>  {
        if (!open()) return emptyList()

        loop(Mode.CHECK_BAT)
        return emptyList()
    }


    protected fun loop(mode: Mode) {
         val logThread = thread(start = true) {
            while (true) {
                try {
                    // If we got interrupted, still empty out the queue, but don't block.
                    val logLine = if (Thread.currentThread().isInterrupted) stdoutQueue.poll() else stdoutQueue.take()
                    if (logLine != null) {
                        println(logLine);
                    }
                    Thread.sleep(100);
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        state = when (mode) {
            Mode.DIRECT -> State.FLUSH
            else -> State.WAIT_FOR_BAT
        }

        while ((state != State.DONE) && (!Thread.currentThread().isInterrupted)) {

            // Read what's available, will block until the timeout if nothing is available.
            val numRead = read(readBuffer)

            // If we are busy flushing, ignore everything read and continue, unless nothing was read.
            if (state == State.FLUSH) {
                if (numRead == 0) {
                    // Nothing read, so we are done with flushing. Decide the next state.
                    state = when (mode) {
                        Mode.DIRECT -> State.MONITOR_BUS
                        else -> State.WAIT_FOR_BAT
                    }
                }
                continue
            }

            // Timeout while waiting for bytes
            if (numRead == 0) {
                if (state == State.WAIT_FOR_BAT) {
                    waited++
                    if (waited % 5 == 0) {
                        if (mode == Mode.CHECK_BAT && waited == 20) {
                            log("No response from battery, assuming not present.")
                            state = State.MONITOR_BUS
                            continue
                        }
                        log("Bus silent, sending wake up byte.")
                        sendWakeUpByte()
                    }

                }
                continue
            }

            // Feed the parser anything we read.
            readBuffer.sliceArray(0 until numRead).forEach { if (state != State.DONE) parser.feed(it.toUByte()) }
        }
        println("Monitor loop ended")
        serialPort.closePort()

        logThread.interrupt()
        logThread.join()
    }

    protected fun log(msg: String) {
        stdoutQueue.put(msg)
    }

    protected fun getMessageLog(): List<Message> {
        return messageLog
    }



    private fun handleMessage(msg: Message) {
        // println("Received msg")
        newMessageHandler(msg)
        //
        if (state == State.WAIT_FOR_BAT) {
            if (msg.tgt() == BOWDEVICE.PC.id) {
                if (msg.isPingOrPong()) {
                    // Respond to a PING sent to us
                    sendPong(msg.src()!!)
                } else if (msg.isHandoff()) {
                    // If we're given control, try to fully 'wake up' the battery.
                    sendWakeUp(BOWDEVICE.BATTERY.id)
                } else if (msg.isRsp() && msg.src() == BOWDEVICE.BATTERY.id && msg.cmd() == BOWCOMMAND.WAKE_UP_BATTERY.id) {
                    // We got a response to our wakeup message.
                    state = State.MONITOR_BUS
                }
            }
        }
        //
        try {
            // println("Message: ${msg.id} ${msg.timestamp} msg:${hex(msg.message)} type:${withName(msg.type, _mapTypesByInt)} src:${withName(msg.source, _mapDeviceByInt)} dst:${withName(msg.target, _mapDeviceByInt)}")
            println("Message: ${msg.id} ${msg.timestamp} msg:${hex(msg.message)}")
        } catch (e: Exception) { println("Error decoding get data: ${e.message}") }


/*         if (state == State.WAIT_RESPONSE) {
            state = when (handleResponse(message)) {
                Result.SEND_COMMAND -> State.SEND_COMMAND
                Result.DONE -> State.DONE
                Result.CONTINUE -> state
            }
        }*/
    }

}