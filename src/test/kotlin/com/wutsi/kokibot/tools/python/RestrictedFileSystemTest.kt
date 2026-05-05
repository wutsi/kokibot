package com.wutsi.kokibot.tools.python

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestrictedFileSystemTest {

    private lateinit var sandbox: Path
    private lateinit var outside: Path
    private lateinit var fs: RestrictedFileSystem

    @BeforeEach
    fun setUp() {
        sandbox = Files.createTempDirectory("koki-restricted-fs-")
        outside = Files.createTempDirectory("koki-restricted-fs-outside-")
        fs = RestrictedFileSystem(sandbox)
    }

    @AfterEach
    fun tearDown() {
        deleteRecursively(sandbox)
        deleteRecursively(outside)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `parsePath - string`() {
        val parsed = fs.parsePath("/tmp/foo")
        assertEquals(Paths.get("/tmp/foo"), parsed)
    }

    @Test
    fun `parsePath - uri`() {
        val parsed = fs.parsePath(java.net.URI.create("file:///tmp/foo"))
        assertEquals(Paths.get("/tmp/foo"), parsed)
    }

    @Test
    fun `toAbsolutePath does not throw for outside paths`() {
        // toAbsolutePath must not enforce — GraalVM relies on it for resolution
        val abs = fs.toAbsolutePath(outside.resolve("x.txt"))
        assertEquals(outside.resolve("x.txt").toAbsolutePath(), abs)
    }

    @Test
    fun `checkAccess - allowed inside sandbox`() {
        val file = Files.createFile(sandbox.resolve("a.txt"))
        fs.checkAccess(file, setOf(java.nio.file.AccessMode.READ))
    }

    @Test
    fun `checkAccess - denied outside sandbox`() {
        val file = Files.createFile(outside.resolve("a.txt"))
        assertThrows<SecurityException> {
            fs.checkAccess(file, setOf(java.nio.file.AccessMode.READ))
        }
    }

    @Test
    fun `createDirectory - allowed inside sandbox`() {
        val dir = sandbox.resolve("sub")
        fs.createDirectory(dir)
        assertTrue(Files.isDirectory(dir))
    }

    @Test
    fun `createDirectory - denied outside sandbox`() {
        assertThrows<SecurityException> {
            fs.createDirectory(outside.resolve("sub"))
        }
    }

    @Test
    fun `delete - allowed inside sandbox`() {
        val file = Files.createFile(sandbox.resolve("a.txt"))
        fs.delete(file)
        assertTrue(!Files.exists(file))
    }

    @Test
    fun `delete - denied outside sandbox`() {
        val file = Files.createFile(outside.resolve("a.txt"))
        assertThrows<SecurityException> { fs.delete(file) }
        assertTrue(Files.exists(file))
    }

    @Test
    fun `newByteChannel - allowed inside sandbox`() {
        val file = sandbox.resolve("a.txt")
        fs.newByteChannel(
            file,
            setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        ).use { ch ->
            ch.write(java.nio.ByteBuffer.wrap("hello".toByteArray()))
        }
        assertEquals("hello", Files.readString(file))
    }

    @Test
    fun `newByteChannel - denied outside sandbox`() {
        val file = outside.resolve("a.txt")
        assertThrows<SecurityException> {
            fs.newByteChannel(
                file,
                setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            )
        }
    }

    @Test
    fun `newDirectoryStream - allowed inside sandbox`() {
        Files.createFile(sandbox.resolve("a.txt"))
        Files.createFile(sandbox.resolve("b.txt"))
        val names = fs.newDirectoryStream(sandbox) { true }.use { stream ->
            stream.map { it.fileName.toString() }.toSet()
        }
        assertEquals(setOf("a.txt", "b.txt"), names)
    }

    @Test
    fun `newDirectoryStream - denied outside sandbox`() {
        assertThrows<SecurityException> {
            fs.newDirectoryStream(outside) { true }
        }
    }

    @Test
    fun `toRealPath - allowed inside sandbox`() {
        val file = Files.createFile(sandbox.resolve("a.txt"))
        val real = fs.toRealPath(file)
        assertEquals(file.toRealPath(), real)
    }

    @Test
    fun `toRealPath - denied outside sandbox`() {
        val file = Files.createFile(outside.resolve("a.txt"))
        assertThrows<SecurityException> { fs.toRealPath(file) }
    }

    @Test
    fun `readAttributes - allowed inside sandbox`() {
        val file = Files.createFile(sandbox.resolve("a.txt"))
        val attrs = fs.readAttributes(file, "basic:size,isRegularFile")
        assertEquals(true, attrs["isRegularFile"])
    }

    @Test
    fun `readAttributes - denied outside sandbox`() {
        val file = Files.createFile(outside.resolve("a.txt"))
        assertThrows<SecurityException> {
            fs.readAttributes(file, "basic:size")
        }
    }

    @Test
    fun `denied for path traversal escape`() {
        // sandbox/../outside-ish path normalizes outside the root
        val sneaky = sandbox.resolve("../escape.txt")
        assertThrows<SecurityException> {
            fs.checkAccess(sneaky, setOf(java.nio.file.AccessMode.READ))
        }
    }
}
