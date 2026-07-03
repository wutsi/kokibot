package com.wutsi.kokibot.service.credential

import com.wutsi.kokibot.ConfigurationException

object NoOpCredentialService : CredentialService {
    override fun get(key: String): String =
        throw ConfigurationException("Credential '$key' not found in credentials.json")

    override fun getOrNull(key: String): String? = null

    override fun set(key: String, value: String, scope: CredentialScope) {}
}
