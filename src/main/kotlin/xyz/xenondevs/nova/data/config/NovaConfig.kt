package xyz.xenondevs.nova.data.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import xyz.xenondevs.nova.IS_VERSION_CHANGE
import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.util.data.getResourceAsStream
import xyz.xenondevs.nova.util.data.getResourceData
import xyz.xenondevs.nova.util.data.getResources
import xyz.xenondevs.nova.util.data.set
import java.io.File

val DEFAULT_CONFIG = NovaConfig["config"]

class NovaConfig(private val configPath: String) : JsonConfig(File("${NOVA.dataFolder}/$configPath"), false) {
    
    private val defaults: JsonConfig
    private val internalConfig = JsonConfig(JsonParser.parseReader(getResourceAsStream(configPath)!!.reader()).asJsonObject)
    
    init {
        extractConfigFiles()
        reload()
        
        val defaultsElement = configDefaults.get(configPath)
            ?: JsonParser.parseReader(file!!.reader()).also { configDefaults.add(configPath, it) }
        defaults = JsonConfig(defaultsElement as JsonObject)
        
        updateUnchangedConfigValues()
    }
    
    private fun extractConfigFiles() {
        file!!.parentFile.mkdirs()
        if (!file.exists()) file.writeBytes(getResourceData(configPath))
    }
    
    private fun updateUnchangedConfigValues() {
        // loop over all elements of the internal config
        for ((key, internalElement) in internalConfig.config.entrySet()) {
            // get what's configured in the user config under that key
            val userConfiguredElement = config.get(key)
            
            // check if the configured element is different from the internal one
            if (internalElement != userConfiguredElement) {
                
                // if this key doesn't exist or doesn't differ from the originally extracted value
                // it's safe to replace it with the internal value
                if (userConfiguredElement == null || (defaults.config.get(key) == userConfiguredElement)) {
                    config[key] = internalElement
                    
                    // also write it to the default config as this is now a default value
                    defaults.config[key] = internalElement
                }
            }
        }
        
        // save changes
        save(true)
    }
    
    override fun get(path: List<String>) =
        super.get(path) ?: internalConfig.get(path)
    
    companion object {
        
        private val configs = HashMap<String, NovaConfig>()
        private val configDefaults = PermanentStorage.retrieve("configDefaults") { JsonObject() }
        
        fun init() {
            LOGGER.info("Loading configs")
            
            getResources("config/")
                .filterNot { it.startsWith("config/recipes/") }
                .forEach {
                    val configName = it.substring(7).substringBeforeLast('.')
                    configs[configName] = NovaConfig(it)
                }
            
            if (IS_VERSION_CHANGE) {
                val defaultConfig = configs["config"]!!
                defaultConfig["resource_pack.url"] = defaultConfig.internalConfig.getString("resource_pack.url")!!
                defaultConfig.save(true)
            }
            
            PermanentStorage.store("configDefaults", configDefaults)
        }
        
        operator fun get(name: String) = configs[name]!!
        
        operator fun get(material: NovaMaterial) = configs["machine/${material.typeName.lowercase()}"]!!
        
    }
    
}