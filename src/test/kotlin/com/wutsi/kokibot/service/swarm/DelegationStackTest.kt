package com.wutsi.kokibot.service.swarm

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DelegationStackTest {
    private val context = Context(
        home = File("target/test-data/delegation-stack"),
        llm = mock<LLM>(),
    )
    private lateinit var stack: DelegationStack

    @BeforeEach
    fun setUp() {
        stack = DelegationStack()
        stack.init(emptyMap<String, Any>(), context)
    }

    @Test
    fun id() {
        assertEquals(DelegationStack.ID, stack.id())
    }

    @Test
    fun `init with default config`() {
        // Default values should be applied
        val stack = DelegationStack()
        stack.init(emptyMap<String, Any>(), context)

        // Should allow pushes up to default depth
        repeat(DelegationStack.DEFAULT_MAX_DEPTH) { i ->
            stack.push("session1", "agent-$i")
        }
        assertEquals(DelegationStack.DEFAULT_MAX_DEPTH, stack.getDepth("session1"))
    }

    @Test
    fun `init with custom config`() {
        val stack = DelegationStack()
        stack.init(
            mapOf(
                "max-depth" to 3,
                "detect-cycles" to false
            ),
            context
        )

        // Should allow pushes up to custom depth
        repeat(3) { i ->
            stack.push("session1", "agent-$i")
        }

        // 4th push should fail
        assertThrows<DelegationException> {
            stack.push("session1", "agent-3")
        }
    }

    @Test
    fun `push allows delegation within depth limit`() {
        // Should not throw
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")
        stack.push("session1", "agent-c")

        assertEquals(3, stack.getDepth("session1"))
        assertEquals(listOf("agent-a", "agent-b", "agent-c"), stack.getStack("session1"))
    }

    @Test
    fun `push throws when max depth exceeded`() {
        // Given: stack at max depth
        repeat(5) { i -> stack.push("session1", "agent-$i") }

        // When/Then: 6th push throws
        val ex = assertThrows<DelegationException> {
            stack.push("session1", "agent-6")
        }
        assertTrue(ex.message!!.contains("depth limit"))
        assertTrue(ex.message!!.contains("agent-0 → agent-1 → agent-2 → agent-3 → agent-4"))
        assertTrue(ex.message!!.contains("agent-6"))
    }

    @Test
    fun `push throws on cycle when detection enabled`() {
        // Given
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")

        // When/Then: pushing 'agent-a' again throws
        val ex = assertThrows<DelegationException> {
            stack.push("session1", "agent-a")
        }
        assertTrue(ex.message!!.contains("cycle detected"))
        assertTrue(ex.message!!.contains("agent-a → agent-b → agent-a"))
    }

    @Test
    fun `push allows cycles when detection disabled`() {
        // Given: stack with detection disabled
        val stack = DelegationStack()
        stack.init(mapOf("detect-cycles" to false), context)

        // When/Then: cycle allowed
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")
        stack.push("session1", "agent-a") // No throw

        assertEquals(3, stack.getDepth("session1"))
        assertEquals(listOf("agent-a", "agent-b", "agent-a"), stack.getStack("session1"))
    }

    @Test
    fun `pop removes top of stack`() {
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")
        stack.push("session1", "agent-c")

        assertEquals("agent-c", stack.pop("session1"))
        assertEquals(2, stack.getDepth("session1"))
        assertEquals(listOf("agent-a", "agent-b"), stack.getStack("session1"))

        assertEquals("agent-b", stack.pop("session1"))
        assertEquals(1, stack.getDepth("session1"))
    }

    @Test
    fun `pop returns null when stack is empty`() {
        assertNull(stack.pop("session1"))
        assertNull(stack.pop("nonexistent"))
    }

    @Test
    fun `clear removes all entries for session`() {
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")
        stack.push("session1", "agent-c")

        stack.clear("session1")

        assertEquals(0, stack.getDepth("session1"))
        assertEquals(emptyList(), stack.getStack("session1"))
    }

    @Test
    fun `clear on empty session does not throw`() {
        // Should not throw
        stack.clear("nonexistent")
    }

    @Test
    fun `getStack returns empty list for nonexistent session`() {
        assertEquals(emptyList(), stack.getStack("nonexistent"))
    }

    @Test
    fun `getStack returns immutable copy`() {
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")

        val stackCopy = stack.getStack("session1")
        assertEquals(listOf("agent-a", "agent-b"), stackCopy)

        // Pushing should not affect the copy
        stack.push("session1", "agent-c")
        assertEquals(listOf("agent-a", "agent-b"), stackCopy) // unchanged
    }

    @Test
    fun `getDepth returns 0 for nonexistent session`() {
        assertEquals(0, stack.getDepth("nonexistent"))
    }

    @Test
    fun `sessions are isolated`() {
        stack.push("session1", "agent-a")
        stack.push("session1", "agent-b")

        stack.push("session2", "agent-c")
        stack.push("session2", "agent-d")

        assertEquals(2, stack.getDepth("session1"))
        assertEquals(2, stack.getDepth("session2"))
        assertEquals(listOf("agent-a", "agent-b"), stack.getStack("session1"))
        assertEquals(listOf("agent-c", "agent-d"), stack.getStack("session2"))
    }

    @Test
    fun `concurrent pushes are thread-safe`() {
        // Run multiple threads pushing to different sessions
        val threads = (1..10).map { i ->
            Thread {
                repeat(3) { j ->
                    stack.push("session$i", "agent-$j")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Verify all sessions have depth 3
        (1..10).forEach { i ->
            assertEquals(3, stack.getDepth("session$i"))
        }
    }

    @Test
    fun `destroy clears all stacks`() {
        stack.push("session1", "agent-a")
        stack.push("session2", "agent-b")

        stack.destroy()

        assertEquals(0, stack.getDepth("session1"))
        assertEquals(0, stack.getDepth("session2"))
    }
}
