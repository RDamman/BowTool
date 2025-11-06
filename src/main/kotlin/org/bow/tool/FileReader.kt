package org.bow.tool

import java.nio.file.Files
import java.nio.file.Path

class FileReaderBin {

    fun readFile(path: Path, handler: (byte: UByte) -> Unit) {
        Files.newInputStream(path).buffered().iterator().forEach { byte -> handler.invoke(byte.toUByte()) }
    }

}


open class ReaderHex (var handler: (byte: UByte) -> Unit) {

    var left = true
    var cur = ""

    fun processChar(char: Char) {
        if (char.isLetterOrDigit()) {
            if (left) {
                cur = "" + char
                left = false
            } else {
                cur += char
                left = true
                //
                val value = Integer.parseInt(cur, 16).toUByte()
                try {
                    handler.invoke(value)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

open class FileReader (handler: (byte: UByte) -> Unit) : ReaderHex(handler) {

    protected fun detectEncoding(file: java.io.File): java.nio.charset.Charset {
        java.io.FileInputStream(file).use { inputStream ->
            val bom = ByteArray(4)
            val n = inputStream.read(bom, 0, bom.size)

            return when {
                n >= 3 && (bom[0].toInt() and 0xFF) == 0xEF && (bom[1].toInt() and 0xFF) == 0xBB && (bom[2].toInt() and 0xFF) == 0xBF -> {
                    java.nio.charset.StandardCharsets.UTF_8 // UTF-8 BOM
                }
                n >= 2 && (bom[0].toInt() and 0xFF) == 0xFF && (bom[1].toInt() and 0xFF) == 0xFE -> {
                    java.nio.charset.StandardCharsets.UTF_16LE // UTF-16 Little Endian
                }
                n >= 2 && (bom[0].toInt() and 0xFF) == 0xFE && (bom[1].toInt() and 0xFF) == 0xFF -> {
                    java.nio.charset.StandardCharsets.UTF_16BE // UTF-16 Big Endian
                }
                else -> {
                    java.nio.charset.StandardCharsets.UTF_8 // or java.nio.charset.StandardCharsets.ISO_8859_1
                }
            }
        }
    }
}


class FileReaderHex (handler: (byte: UByte) -> Unit) : FileReader(handler) {

    fun readFile(path: Path) {
        val file = path.toFile()
        val charset = detectEncoding(file)
        java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(file), charset)).use { reader ->
            var char: Char
            while (reader.read().also { char = it.toChar() } != -1) {
                processChar(char)
            }
        }
    }
}


@OptIn(ExperimentalUnsignedTypes::class)
class FileReaderBow (handler: (byte: UByte) -> Unit, private val commentHandler: (timestamp: Long, comment: String) -> Unit) : FileReader(handler) {

    fun readFile(path: Path) {
        val file = path.toFile()
        val charset = detectEncoding(file)
        java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(file), charset)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\t")
                parts[0].trim().forEach { char -> processChar(char) }
                val timestamp = if (parts.size > 1) parts[1].trim() else ""
                // val id = if (parts.size > 2) parts[2].trim().toInt() else 0
                val comment = if (parts.size > 3) EscapeString.safeUnescape(parts[3].trim()) else ""
                commentHandler.invoke(if (timestamp.isNotEmpty()) timestamp.toLong() else System.currentTimeMillis(), comment)
            }
        }
    }


}