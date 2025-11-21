package org.bow.tool

import javafx.scene.control.*
import javafx.collections.ObservableList
import javafx.collections.FXCollections.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.collections.ArrayList

@OptIn(ExperimentalUnsignedTypes::class)
class Config(
    var types: BowItems,
    var devices: BowItems,
    var commands: BowItems,
    var dataIds: BowItems,
    var starts: BowItems
)

{

    private var _listTypes: ObservableList<BowItem?>? = null
    private var _listDevices: ObservableList<BowItem?>? = null
    private var _listDataIds: ObservableList<BowItem?>? = null
    private var _listCommands: ObservableList<BowItem?>? = null
    private var _listLengths: ObservableList<BowItem?>? = null
    private var _listStarts: ObservableList<BowItem?>? = null

    var typesList: ObservableList<BowItem?> get() { if (_listTypes == null) { _listTypes = createLookupList(types, 16) }; return _listTypes!! } set(value) { _listTypes = value }
    var devicesList: ObservableList<BowItem?> get() { if (_listDevices == null) { _listDevices = createLookupList(devices, 16) }; return _listDevices!! } set(value) { _listDevices = value }
    var commandsList: ObservableList<BowItem?> get() { if (_listCommands == null) { _listCommands = createLookupList(commands, 256) }; return _listCommands!! } set(value) { _listCommands = value }
    var dataIdsList: ObservableList<BowItem?> get() { if (_listDataIds == null) { _listDataIds = createLookupList(dataIds, 256) }; return _listDataIds!! } set(value) { _listDataIds = value }
    var lengthsList: ObservableList<BowItem?> get() { if (_listLengths == null) { _listLengths = createLengthList() }; return _listLengths!! } set(value) { _listLengths = value }
    var startsList: ObservableList<BowItem?> get() { if (_listStarts == null) { _listStarts = observableArrayList(starts.items.values.toList().sortedBy { it.id })  }; return _listStarts!! } set(value) { _listStarts = value }

    fun createLookupList(bowitems: BowItems, range: Int): ObservableList<BowItem?>? {
        val list: MutableList<BowItem?> = ArrayList()
        for (i in 0 until range) {
            //val id = i.toUByte()
            val item = bowitems.findById(i)
            if (item != null) {
                list.add(item)
            } else {
                list.add(BowItem(i.toUByte(), "$i", ""))
            }
        }
        // add a null at index 0 to reserve that slot
        list.add(0, null)
        return javafx.collections.FXCollections.observableArrayList(list)
    }

    fun createLengthList(): ObservableList<BowItem?> {
        val list: MutableList<BowItem?> = ArrayList()
        for (i in 0 until 16) {
            val item = BowItem(i.toUByte(), "Length $i", "LEN$i")
            list.add(item)
        }
        // add a null at index 0 to reserve that slot
        list.add(0, null)
        return javafx.collections.FXCollections.observableArrayList(list)
    }

    companion object {
        private var _instance: Config? = null

        fun getInstance(): Config {
            if (_instance == null)
                _instance = Config.load()
            return _instance!!
        }

        fun refreshConfig(): Config {
            _instance = null
            return getInstance()
        }

        private fun load(): Config {
            val loadedConfig = loadConfig()
            return Config(
                BowItems(BOWTYPE::class, loadedConfig.types),
                BowItems(BOWDEVICE::class, loadedConfig.devices),
                BowItems(BOWCOMMAND::class, loadedConfig.commands),
                BowItems(BOWDATA::class, loadedConfig.dataIds),
                BowItems(BOWSTART::class, emptyMap())
            )
        }

        fun loadConfig(): ConfigFileData {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule.Builder().build())

            val appConfigPath = Paths.get("app", "config.yml")
            val localConfigPath = Paths.get("config.yml")
            val configPath = if (Files.exists(appConfigPath)) appConfigPath else localConfigPath

            return Files.newBufferedReader(configPath).use {
                mapper.readValue(it, ConfigFileData::class.java)
            }
        }            
    }

    var decoder: Decoder? = null
        get () {
            if (field == null)
            {
                synchronized(this) { 
                    if (field == null)
                        field = Decoder(this)
                    }
            }
            return field
        }
    
    public fun decodeMessage(msg: Message): String
    {
        val decoderLocal = decoder!!
        return if (decoderLocal.check(msg).isEmpty()) decoderLocal.decode(msg) else decoderLocal.check(msg) 
    }
}
    
data class ConfigFileData(
    var types: Map<String, String>,
    var devices: Map<String, String>,
    var dataIds: Map<String, String>,
    var commands: Map<String, String>
)



enum class BOWDEVICE(val id: UByte, val Name: String, val Code: String) {
    MOTOR(0x0u, "Motor", "MTR"),
    BATTERY(0x2u, "Battery", "BAT"),
    PC(0x4u, "PC", "PC"),
    CHARGER(0x6u, "Charger", "CHR"),
    KEYPAD(0x8u, "Keypad", "KEY"),
    TORQUESENSOR(0xAu, "Torque Sensor", "TOR"),
    DISPLAY(0xCu, "Display", "DIS"),
    UNKNOWN(0xEu, "Unknown", "UNK");
}

enum class BOWCOMMAND(val id: UByte, val Name: String, val Code: String) {
    GET_DATA(0x08u, "Get Data", "GET"),
    PUT_DATA(0x09u, "Put Data", "PUT"),
    WAKE_UP_BATTERY(0x14u, "CU3: Wake Up Battery", "WUB"),
    SHOW_ERROR(0x17u, "Show Error", "ERR"),
    GET_DISPLAY_SERIAL(0x20u, "Get Display Serial#", "GDS"),
    CU2_UPDATE_DISPLAY(0x26u, "CU2 Update Display", "C2U"),
    CU2_UPDATE_DISPLAY_2(0x27u, "CU2 Update Display 2", "C2U"),
    CU3_UPDATE_DISPLAY(0x28u, "CU3 Update Display", "C3U"),
    SET_ASSIST_LEVEL(0x34u, "Set Assist Level", "SAL")
}


enum class BOWTYPE(val id: UByte, val Name: String, val Code: String) {
    HANDOFF(0x0u, "HANDOFF to", "HND"),
    REQUEST(0x1u, "REQUEST", "REQ"),
    RESPONSE(0x2u, "RESPONSE", "RSP"),
    PONG(0x3u, "PONG!", "PONG"),
    PING(0x4u, "PING!", "PING"),
    UNKNOWN(0x5u, "UNKNOWN", "UNK");
}

enum class BOWDATA(val id: UByte, val Name: String, val Code: String) {
    PAIRED_SERIAL1(0x5bu, "Paired serial 1", "PS1"),
    PAIRED_SERIAL2(0x5cu, "Paired serial 2", "PS2")
}

enum class BOWSTART(val id: UByte, val Name: String, val Code: String) {
    WAKEUP(0x0bu, "Wakeup", "WKUP"),
    MESSAGE(0x10u, "Message", "MSG")
}



data class BowItem(val id: UByte, val Name: String, val Code: String)
{
}

class BowItems(enumClass: KClass<out Enum<*>>, values: Map<String, String>)
{
    public val items: Map<Int, BowItem> = enumClass.java.enumConstants.map { 
        val idProp = enumClass.memberProperties.first { it.name == "id" } as KProperty1<Any, UByte>
        val nameProp = enumClass.memberProperties.first { it.name == "Name" } as KProperty1<Any, String>
        val codeProp = enumClass.memberProperties.first { it.name == "Code" } as KProperty1<Any, String>
        val it = it as Any
        BowItem(idProp.get(it), nameProp.get(it), codeProp.get(it)) }.associateBy { it.id.toInt() }

    init {
        // Override names from config
        for ((key, value) in values) {
            val id = Integer.decode(key).toUByte()
            var item = items[id.toInt()]
            val newItem = BowItem(id, value, item?.Code ?: value)
            (items as MutableMap)[id.toInt()] = newItem
        }
    }

    
    fun findById(id: Int): BowItem? {
        return items[id]
    }   

    fun findNameById(id: Int): String? {
        return items[id]?.Name
    }
}

fun List<BowItem?>.findById(id: Int): BowItem? {
    return firstOrNull { it?.id?.toInt() == id }
}

