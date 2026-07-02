package com.wutsi.kokibot.service.credential

enum class CredentialScope { LOCAL, GLOBAL }

interface CredentialService {
    fun get(key: String): String
    fun getOrNull(key: String): String?
    fun set(key: String, value: String, scope: CredentialScope = CredentialScope.LOCAL)
}
