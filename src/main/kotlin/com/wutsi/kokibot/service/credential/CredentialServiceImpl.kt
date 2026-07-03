package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.util.MapUtil
import tools.jackson.databind.json.JsonMapper
import java.io.File

class CredentialServiceImpl(
    private val globalFile: File,
    private val localFile: File,
    private val jsonMapper: JsonMapper = JsonMapper(),
) : CredentialService {
    private val globalCredentials: MutableMap<String, String> = mutableMapOf()
    private val localCredentials: MutableMap<String, String> = mutableMapOf()

    init {
        load(globalFile, globalCredentials)
        load(localFile, localCredentials)
    }

    override fun getOrNull(key: String): String? =
        localCredentials[key] ?: globalCredentials[key]

    override fun get(key: String): String =
        getOrNull(key) ?: throw ConfigurationException("Credential '$key' not found in credentials.json")

    override fun set(key: String, value: String, scope: CredentialScope) {
        val map = if (scope == CredentialScope.LOCAL) localCredentials else globalCredentials
        val file = if (scope == CredentialScope.LOCAL) localFile else globalFile
        map[key] = value
        persist(file, map)
    }

    private fun load(file: File, target: MutableMap<String, String>) {
        if (!file.exists()) return
        try {
            val raw = jsonMapper.readValue(file, Map::class.java)
            val resolved = MapUtil.applyEnv(raw)
            resolved.forEach { (k, v) -> if (k != null && v != null) target[k.toString()] = v.toString() }
        } catch (ex: Exception) {
            throw ConfigurationException("Failed to parse ${file.name}: ${ex.message}")
        }
    }

    private fun persist(file: File, map: Map<String, String>) {
        file.parentFile?.mkdirs()
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, map)
    }
}
