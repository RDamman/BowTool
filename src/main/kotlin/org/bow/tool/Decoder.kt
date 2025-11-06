package org.bow.tool

@OptIn(ExperimentalUnsignedTypes::class)
class Decoder(private val config: Config) {

    private val getDataDecoder = GetDataDecoder(config.dataIds)

    fun check(message: Message) = buildString {
        if (message.message.size != message.size!!.toInt()) {
            append(" SIZE MISMATCH")
        }

        if (CRC8.crc8Bow(message.message.dropLast(1)) != message.message.last()) {
            append(" CRC MISMATCH")
        }
    }

    fun decode(message: Message): String {
        return when (message.type) {
            BOWTYPE.PING.id -> "${BOWTYPE.PING.Name} ${config.devices.findById(message.src()?.toInt()!!)!!.Name}=>${config.devices.findById(message.tgt()?.toInt()!!)!!.Name}"
            BOWTYPE.PONG.id -> "${BOWTYPE.PONG.Name} ${config.devices.findById(message.src()?.toInt()!!)!!.Name}=>${config.devices.findById(message.tgt()?.toInt()!!)!!.Name}"
            BOWTYPE.HANDOFF.id -> "${BOWTYPE.HANDOFF.Name} ${config.devices.findById(message.tgt()?.toInt()!!)!!.Name}"
            BOWTYPE.REQUEST.id, BOWTYPE.RESPONSE.id, BOWTYPE.UNKNOWN.id -> decodeRequestOrResponse(message)
            else -> ""
        }
    }

    private fun decodeRequestOrResponse(message: Message): String {
        var decoded = ""
        val cmdName = withName2(message.message[3], config.commands)
        val data = message.data()

        // Defaults
        if (message.isReq()) {
            decoded = "$cmdName ${hex(data)}"
        } else if (message.isRsp()) {
            decoded = "$cmdName - OK ${hex(data)}"
        }

        when (message.cmd()) {
            BOWCOMMAND.GET_DATA.id -> if (message.isReqOrRsp()) decoded = "$cmdName ${if (message.isRsp()) " - OK" else ""} ${getDataDecoder.createGetDataString(message)}"
            BOWCOMMAND.PUT_DATA.id -> if (message.isReqOrRsp()) decoded = "$cmdName ${if (message.isRsp()) " - OK" else ""} ${createPutDataString(message)}"
            BOWCOMMAND.SHOW_ERROR.id -> if (message.isReq()) decoded = "$cmdName: E-00${hex(data.slice(0 until 1))}"
            BOWCOMMAND.GET_DISPLAY_SERIAL.id -> if (message.isRsp()) decoded = "$cmdName - OK ${hex(data.slice(0 until 2))} ${hex(data.slice(6 until 8))} (${hex(data)})"
            // CU2 Display update
            BOWCOMMAND.CU2_UPDATE_DISPLAY.id, BOWCOMMAND.CU2_UPDATE_DISPLAY_2.id -> if (message.isReq()) {
                decoded += createCu2UpdateDisplayString(message)
            }
            // CU3 Display update
            BOWCOMMAND.CU3_UPDATE_DISPLAY.id -> if (message.isReq()) {
                decoded += ": "
                decoded += when (data[0].toInt()) {
                    0x00 -> "SCR:MAIN"
                    0x01 -> "SCR:BAT+CHRG"
                    0x02 -> "SCR:BAT"
                    0x03 -> "SCR:MAIN"
                    else -> "SCR:???"
                }
                decoded += "(${data[0]}) "

                decoded += when (data[1].toInt()) {
                    0x00 -> "ASS:OFF"
                    0x01 -> "ASS:1 or 2"
                    0x02 -> "ASS:2 or 3"
                    0x03 -> "ASS:3 or 4"
                    0x04 -> "ASS:P"
                    0x05 -> "ASS:R"
                    0x06 -> "ASS:4 or 1"
                    0x07 -> "ASS:5"
                    else -> "ASS:???"
                }
                decoded += "(${data[1]}) "

                if (isBitSet(data[2], 3)) {
                    decoded += "SCR:ON "
                }

                if (isBitSet(data[2], 0)) {
                    decoded += "LIGHT "
                }

                if (isBitSet(data[2], 2)) {
                    decoded += "RANGE_EXT "
                }

                decoded += "speed:${data[3].toInt().shl(8) + data[4].toInt()} "
                decoded += "trip1:${data[5].toInt().shl(24) + data[6].toInt().shl(16) + data[7].toInt().shl(8) + data[8].toInt()} "
                decoded += "trip2:${data[9].toInt().shl(24) + data[10].toInt().shl(16) + data[11].toInt().shl(8) + data[12].toInt()} "
            }

            BOWCOMMAND.SET_ASSIST_LEVEL.id -> if (message.isReq()) {
                decoded += when (val value = data[0].toInt()) {
                    0x00 -> ""
                    0x01 -> " > ECO"
                    0x02 -> " > NORMAL"
                    0x03 -> " > POWER"
                    else -> " > ???($value)"
                }
            }
        }
        return decoded
    }

    private fun createBlinkString(name: String, input: UByte, shift: Int) = buildString {
        val value = input.rotateRight(shift) and 0x03u
        if (value.toUInt() != 0x00u) {
            append(name)
            append(
                when (value.toUInt()) {
                    0x00u -> ""
                    0x01u -> ":FST"
                    0x02u -> ":SLW"
                    0x03u -> ":SOL"
                    else -> throw IllegalStateException("Got value which should not be possible: $value")
                }
            )
            append(" ")
        }
    }

    private fun createPutDataString(message: Message) = buildString {
        if (message.isReq()) {
            val data = message.data()

            val result = ArrayList<PutDataPart>()
            var index = 0
            do {
                val type = TypeFlags(data[index])

                val headerSize = if (type.array) 5 else 2

                val elementCount: Int
                val offset : Int?
                if (type.array) {
                    val from = data[index + 2].toInt()
                    val to = data[index + 3].toInt()
                    val length = data[index + 4].toInt()
                    elementCount = length
                    offset = from
                } else {
                    elementCount = 1
                    offset = null
                }

                val partSize = headerSize + (type.elementSize * elementCount)

                val part = data.slice(index until index + partSize)
                index += partSize

                val elementsData = part.drop(headerSize)
                val elements = ArrayList<List<UByte>>()
                if (type.array) {
                    if (type.size == 0) {
                        // Array of bytes
                        elements.add(elementsData)
                    } else {
                        // Array of sized elements
                        for (elementIndex in 0 until elementCount) {
                            elements.add(elementsData.slice(elementIndex * type.elementSize until (elementIndex + 1) * type.elementSize))
                        }
                    }
                } else {
                    elements.add(elementsData)
                }

                result.add(PutDataPart(type, part[1], offset, elements))
            } while (type.more)

            if (index != data.size) throw java.lang.IllegalStateException("Invalid put data request")

            result.forEach { reqPart -> append(reqPart) }
        }
    }

    inner class PutDataPart(val type: TypeFlags, val id: UByte, val offset: Int?, val elements: List<List<UByte>>) {
        override fun toString() = buildString {
            append(" ${hex(type.typeValue)}:${hex(id)}")
            append("(${config.dataIds.findNameById(id.toInt()) ?: "Unknown"})")
            if (type.array) append("[${offset}]")
            append(dataToString())
        }

        fun dataToString() = buildString {
            append(": ")
            append(
                if (type.array && type.size != 0) {
                    elements.joinToString(prefix = "[", postfix = "]", transform = type.formatter)
                } else {
                    type.formatter(elements.first())
                }
            )
        }
    }

    private fun createCu2UpdateDisplayString(msg: Message) = buildString {
        val data = msg.data()
        append(": ")
        append(createBlinkString("OFF", data[0], 0))
        append(createBlinkString("ECO", data[0], 2))
        append(createBlinkString("NRM", data[0], 4))
        append(createBlinkString("POW", data[0], 6))

        append(createBlinkString("WRE", data[1], 0))
        append(createBlinkString("TOT", data[1], 2))
        append(createBlinkString("TRP", data[1], 4))
        append(createBlinkString("LIG", data[1], 6))

        append(createBlinkString("BAR", data[2], 0))
        append(createBlinkString("COM", data[2], 4))
        append(createBlinkString("KM", data[2], 6))

        append("%02d%% ".format(data[3].toInt()))

        val spd = hex(data.slice(4 until 6))
        append("'${spd.slice(1 until 3)}.${spd.substring(3)}' ")

        append("'${hex(data.slice(6 until 9)).substring(1).replace('c', ' ').replace('a', '-')}' ")
    }
}