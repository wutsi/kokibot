package com.wutsi.kokibot.service.swarm

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.llm.LLMStreamData
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
 * - Stream callback propagation: Maintains LLMStreamData callback for each delegation level
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

    /**
     * Represents a single entry in the delegation stack.
     */
    data class DelegationEntry(
        val agentName: String,
        val streamCallback: ((LLMStreamData) -> Unit)?
    )

    // Configuration
    private var maxDepth: Int = DEFAULT_MAX_DEPTH
    private var detectCycles: Boolean = DEFAULT_DETECT_CYCLES
    private lateinit var context: Context

    // State: sessionId -> Stack<DelegationEntry>
    private val stacks = ConcurrentHashMap<String, MutableList<DelegationEntry>>()
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
     * @param streamCallback Optional callback for streaming responses with usage data
     * @throws DelegationException if validation fails (max depth or cycle)
     */
    fun push(sessionId: String, agentName: String, streamCallback: ((LLMStreamData) -> Unit)? = null) {
        lock.write {
            val stack = stacks.getOrPut(sessionId) { mutableListOf() }

            // Check max depth
            if (stack.size >= maxDepth) {
                val chain = stack.joinToString(" → ") { it.agentName }
                throw DelegationException(
                    "Delegation depth limit ($maxDepth) exceeded. " +
                        "Current chain: $chain. " +
                        "Cannot delegate to '$agentName'."
                )
            }

            // Check cycles (if enabled)
            if (detectCycles && stack.any { it.agentName == agentName }) {
                val chain = (stack.map { it.agentName } + agentName).joinToString(" → ")
                throw DelegationException(
                    "Delegation cycle detected: $chain. " +
                        "Agent '$agentName' is already in the delegation chain."
                )
            }

            stack.add(DelegationEntry(agentName, streamCallback))
        }
    }

    /**
     * Pop the top agent from the delegation stack.
     *
     * @param sessionId The session (request) ID
     * @return The delegation entry that was popped, or null if stack is empty
     */
    fun pop(sessionId: String): DelegationEntry? {
        return lock.write {
            val stack = stacks[sessionId]
            return stack?.removeLastOrNull()
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
        }
    }

    /**
     * Get the current delegation stack for a session.
     *
     * @param sessionId The session (request) ID
     * @return Immutable copy of the stack (oldest first, newest last)
     */
    fun getStack(sessionId: String): List<DelegationEntry> {
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

    /**
     * Get the stream callback from the current delegation (top of stack).
     *
     * @param sessionId The session (request) ID
     * @return The stream callback from the current delegation, or null if stack is empty or no callback
     */
    fun getCurrentStreamCallback(sessionId: String): ((LLMStreamData) -> Unit)? {
        return lock.read {
            val stack = stacks[sessionId]
            stack?.lastOrNull()?.streamCallback
        }
    }

    /**
     * Get the stream callback from the parent delegation (one level up).
     *
     * @param sessionId The session (request) ID
     * @return The stream callback from the parent delegation, or null if no parent or no callback
     */
    fun getParentStreamCallback(sessionId: String): ((LLMStreamData) -> Unit)? {
        return lock.read {
            val stack = stacks[sessionId]
            if (stack.isNullOrEmpty() || stack.size < 2) {
                null
            } else {
                // Get the second-to-last entry (parent)
                stack[stack.size - 2].streamCallback
            }
        }
    }
}
