package com.wutsi.kokibot.service.swarm

/**
 * Thrown when delegation validation fails due to:
 * - Maximum depth exceeded
 * - Cycle detected
 * - Invalid session state
 */
class DelegationException(message: String) : RuntimeException(message)
