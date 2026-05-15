package com.wutsi.kokibot.service.swarm

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Tracks delegation chains across agents to prevent stack overflow issues.
 *
 * Features:
 * - Max depth validation: Prevents chains like A→B→C→D→E→F exceeding threshold
 * - Cycle detection: Prevents circular delegation like A→B→C→A
 * - Thread-safe: Uses ReentrantReadWriteLock for concurrent access
 * - Session isolation: Each session (request) has independent stack
 *
 * Configuration (via swarm section in settings.json):
 * - max-depth: Maximum delegation depth (default: 5)
 * - detect-cycles: Enable cycle detection (default: true)
 */
class DelegationStack : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DelegationStack::class.java)

        const val ID = "service:delegation-stack"
        const val DEFAULT_MAX_DEPTH = 5
        const val DEFAULT_DETECT_CYCLES = true
    }

    // Configuration
    private var maxDepth: Int = DEFAULT_MAX_DEPTH
    private var detectCycles: Boolean = DEFAULT_DETECT_CYCLES
    private lateinit var context: Context

    // State: sessionId -> Stack<AgentName>
    private val stacks = ConcurrentHashMap<String, MutableList<String>>()
    private val lock = ReentrantReadWriteLock()

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.maxDepth = MapUtil.toInt("max-depth", config) ?: DEFAULT_MAX_DEPTH
        this.detectCycles = MapUtil.toBoolean("detect-cycles", config) ?: DEFAULT_DETECT_CYCLES

        LOGGER.info("DelegationStack initialized")
        LOGGER.info("  max-depth: $maxDepth")
        LOGGER.info("  detect-cycles: $detectCycles")
    }

    override fun destroy() {
        lock.write {
            stacks.clear()
        }
        LOGGER.info("DelegationStack destroyed")
    }

    /**
     * Push an agent onto the delegation stack.
     *
     * @param sessionId The session (request) ID
     * @param agentName The agent being delegated to
     * @throws DelegationException if validation fails (max depth or cycle)
     */
    fun push(sessionId: String, agentName: String) {
        lock.write {
            val stack = stacks.getOrPut(sessionId) { mutableListOf() }

            // Check max depth
            if (stack.size >= maxDepth) {
                val chain = stack.joinToString(" → ")
                throw DelegationException(
                    "Delegation depth limit ($maxDepth) exceeded. " +
                        "Current chain: $chain. " +
                        "Cannot delegate to '$agentName'."
                )
            }

            // Check cycles (if enabled)
            if (detectCycles && agentName in stack) {
                val chain = (stack + agentName).joinToString(" → ")
                throw DelegationException(
                    "Delegation cycle detected: $chain. " +
                        "Agent '$agentName' is already in the delegation chain."
                )
            }

            stack.add(agentName)
            LOGGER.debug("Pushed '$agentName' to stack for session $sessionId. Depth: ${stack.size}")
        }
    }

    /**
     * Pop the top agent from the delegation stack.
     *
     * @param sessionId The session (request) ID
     * @return The agent name that was popped, or null if stack is empty
     */
    fun pop(sessionId: String): String? {
        return lock.write {
            val stack = stacks[sessionId]
            val popped = stack?.removeLastOrNull()
            if (popped != null) {
                LOGGER.debug("Popped '$popped' from stack for session $sessionId. Depth: ${stack.size}")
            }
            popped
        }
    }

    /**
     * Clear the entire delegation stack for a session.
     *
     * @param sessionId The session (request) ID
     */
    fun clear(sessionId: String) {
        lock.write {
            stacks.remove(sessionId)
            LOGGER.debug("Cleared stack for session $sessionId")
        }
    }

    /**
     * Get the current delegation stack for a session.
     *
     * @param sessionId The session (request) ID
     * @return Immutable copy of the stack (oldest first, newest last)
     */
    fun getStack(sessionId: String): List<String> {
        return lock.read {
            stacks[sessionId]?.toList() ?: emptyList()
        }
    }

    /**
     * Get the current delegation depth for a session.
     *
     * @param sessionId The session (request) ID
     * @return The depth (0 = no delegation)
     */
    fun getDepth(sessionId: String): Int {
        return lock.read {
            stacks[sessionId]?.size ?: 0
        }
    }
}
