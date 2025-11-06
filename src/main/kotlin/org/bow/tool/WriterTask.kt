package org.bow.tool

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.concurrent.Task
import java.nio.file.Path
import java.nio.file.Files



@OptIn(ExperimentalUnsignedTypes::class)
class WriterTask(private val path: Path, private val listMessages: List<Message>, val readyErrorHandler: (
        iType: Int, sMessage: String) -> Unit) : Task<Int>() {
    @Throws(Exception::class)
    override fun call(): Int {

        var count = 0
        try {
            Files.newOutputStream(path).bufferedWriter().use { writer ->
                for (message in listMessages) {
                    writer.write("${hex(message.message)}\t${message.timestamp}\t${message.id}\t${EscapeString.escape(message.comment)}")
                    writer.newLine()
                    count++
                }
            }
        } catch (e: Exception) {
            readyErrorHandler(1, e.message ?: "Unknown error writing messages to file")
        }
        readyErrorHandler(0, "Successfully wrote $count messages to file")
        return count
    }
}