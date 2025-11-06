package org.bow.tool

import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task
import java.nio.file.Path


@OptIn(ExperimentalUnsignedTypes::class)
abstract class ReaderTask(val deviceByInt: BowItems, val decoder: Decoder, val readyHandler: (
        listMessages: List<Message>) -> Unit) : Task<ObservableList<Message>>() {

    var messageLast: Message? = null

    protected val parser = MessageParser({ message ->

        val errors = decoder.check(message)
        val decoded = if (errors.isEmpty()) decoder.decode(message) else errors

        // println()
        print("tgt:${withName2(message.target, deviceByInt, false)} typ:${message.type}")
        print(
            when (message.type) {
                BOWTYPE.HANDOFF.id -> "        [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 2))}-${hex(message.message.takeLast(1))}]"
                BOWTYPE.PONG.id, BOWTYPE.PING.id -> " src:${withName2(message.source, deviceByInt, false)} [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 3))}-${hex(message.message.takeLast(1))}]"
                else -> " src:${withName2(message.source, deviceByInt, false)} [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 3))}-${
                    hex(
                        message.message.drop(3).dropLast(1)
                    )
                }-${hex(message.message.takeLast(1))}] [${hex(message.message.drop(3).dropLast(1))}]"
            }
        )
        //
        println("$decoded")
        messageLast = message
        Platform.runLater { messages.get().add(message) }
        }, 
        { message -> {
            println("Incomplete: ${hex(message)}, crc:${hex(CRC8.crc8Bow(message.dropLast(1)))}") }
            //
            messageLast = null // Incomplete message // create a dummy Message object??????
            //Platform.runLater { messages.get().add(Message(0u, 0u, null, null, message, null)) }

        })

        fun addCommentTimestampToLastMessage(comment: String, timestamp: Long) {
            messageLast?.let {
                it.comment = comment
                it.timestamp = timestamp
            }
        }



    protected val messages = ReadOnlyObjectWrapper(
        this, "messages",
        FXCollections.observableArrayList(ArrayList<Message>())
    )

    fun getMessages(): ObservableList<Message> {
        return messages.get()
    }

    fun messagesProperty(): ReadOnlyObjectProperty<ObservableList<Message>> {
        return messages.readOnlyProperty
    }

}

@OptIn(ExperimentalUnsignedTypes::class)
class FileReaderTask(private val path: Path, private val iFileType: Int, deviceByInt: BowItems, decoder: Decoder, readyHandler: (listMessages: List<Message>) -> Unit) : ReaderTask(deviceByInt, decoder, readyHandler) {

    @Throws(Exception::class)
    override fun call(): ObservableList<Message> {
        try {
            when (iFileType) {
                1 -> FileReaderBin().readFile(path, { byte -> parser.feed(byte) })
                2 -> FileReaderHex({ byte -> parser.feed(byte) }).readFile(path)
                else -> FileReaderBow({ byte -> parser.feed(byte) }, { timestamp, comment -> addCommentTimestampToLastMessage(comment, timestamp) }).readFile(path)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            readyHandler(messages.get())
        }
        return messages.get()
    }
}


@OptIn(ExperimentalUnsignedTypes::class)
class ClipboardReaderTask(private val content: String, deviceByInt: BowItems, decoder: Decoder, readyHandler: (listMessages: List<Message>) -> Unit) : ReaderTask(deviceByInt, decoder, readyHandler) {

    @Throws(Exception::class)
    override fun call(): ObservableList<Message> {
        try {
            /* 
                val parts = line.split("\t")
                 if (parts.isNotEmpty()) {
                    try {
                        val rawData = parts[0].trim()
                        val timestamp = if (parts.size > 1) parts[1].trim() else ""
                        // val id = if (parts.size > 2) parts[2].trim().toInt() else 0
                        val comment = if (parts.size > 3) EscapeString.safeUnescape(parts[3].trim()) else ""
                        val bytes = hexToBytes(rawData)
                        if (bytes.isNotEmpty()) {
                            val message = Message(bytes.toList(), timestamp, id, comment)
                            result.add(message)
                        } */
            val lines = content.split("\n")
            for (line in lines) {
                var left = true
                var cur = ""
                val parts = line.split("\t")
                parts[0].trim().forEach { char ->
                    if (char.isLetterOrDigit()) {
                        if (left) {
                            cur = "" + char
                            left = false
                        } else {
                            cur += char
                            left = true

                            val value = Integer.parseInt(cur, 16).toUByte()
                            parser.feed(value)
                        }
                    } }
                val timestamp = if (parts.size > 1) parts[1].trim() else ""
                val comment = if (parts.size > 3) EscapeString.safeUnescape(parts[3].trim()) else ""
                addCommentTimestampToLastMessage(comment, timestamp.toLongOrNull() ?: 0L)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            readyHandler(messages.get())
        }
        return messages.get()
    }
}

    object EscapeString {
        @JvmField val charsEscaped = arrayOf("\r", "\n", "\t", "\b", "'", "\"", "\\")
        @JvmField val validEscapes  = arrayOf("\\\\r", "\\\\n", "\\\\t", "\\\\b", "\\\\'", "\\\\\"", "\\\\\\\\")

        @JvmStatic fun escape(input: String): String {
        return input.replace("\\", "\\\\")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
    }

    @JvmStatic fun safeUnescape(sIn: String): String {

        var result = sIn
        for (i in validEscapes.indices) {
            result = result.replace(validEscapes[i], charsEscaped[i])
        }
        return result
    }
    }
