package org.bow.tool

@OptIn(ExperimentalUnsignedTypes::class)
data class Message(val type: UByte, val target: UByte, val source: UByte?, val size: UByte?, val message: UByteArray, val previous: Message?) 
{
        fun cmd() = message[3].toUByte()
        fun tgt() = target.toUByte()
        fun src() = source?.toUByte()
        fun isHandoff() = isType(BOWTYPE.HANDOFF.id)
        fun isPingOrPong() = isType(BOWTYPE.PONG.id) || isType(BOWTYPE.PING.id)
        fun isReq() = isType(BOWTYPE.REQUEST.id)
        fun isRsp() = isType(BOWTYPE.RESPONSE.id)
        fun isUnknown() = isType(BOWTYPE.UNKNOWN.id)
        fun isReqOrRsp() = isReq() || isRsp() || isUnknown()
        fun isCmd(cmd: UByte) = isReqOrRsp() && cmd() == cmd
        fun data() = message.drop(4).dropLast(1)
        private fun isType(check: UByte) = type == check

        var id: Int = generateId()
        var timestamp: Long = System.currentTimeMillis()
        var comment: String = ""

        companion object IdGenerator {
            private var _iId = 0

            fun generateId(): Int {
                synchronized(IdGenerator) {
                    return _iId++
                }
            }

            fun resetId() {
                synchronized(IdGenerator) {
                    _iId = 0
                }
            }
        }
}



