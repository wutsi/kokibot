package com.wutsi.kokibot.tools.python

import org.graalvm.polyglot.io.FileSystem
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.DirectoryStream
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute

/**
 * A [FileSystem] that restricts all I/O operations to a single root directory.
 *
 * Any attempt to access a path outside [allowedRoot] (after normalization) will
 * raise a [SecurityException]. This is intended to be used to sandbox guest
 * languages (e.g. Python) executed via GraalVM Polyglot.
 */
class RestrictedFileSystem(allowedRoot: Path) : FileSystem {

    private val allowedRoot: Path = allowedRoot.toAbsolutePath().normalize()
    private val delegate: FileSystem = FileSystem.newDefaultFileSystem()

    private fun checkPath(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(allowedRoot)) {
            throw SecurityException("Access denied to: $normalized")
        }
    }

    override fun parsePath(uri: URI): Path = delegate.parsePath(uri)

    override fun parsePath(path: String): Path = delegate.parsePath(path)

    override fun checkAccess(path: Path, modes: Set<AccessMode>, vararg linkOptions: LinkOption) {
        checkPath(path)
        delegate.checkAccess(path, modes, *linkOptions)
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        checkPath(dir)
        delegate.createDirectory(dir, *attrs)
    }

    override fun delete(path: Path) {
        checkPath(path)
        delegate.delete(path)
    }

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>,
    ): SeekableByteChannel {
        checkPath(path)
        return delegate.newByteChannel(path, options, *attrs)
    }

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>,
    ): DirectoryStream<Path> {
        checkPath(dir)
        return delegate.newDirectoryStream(dir, filter)
    }

    override fun toAbsolutePath(path: Path): Path = path.toAbsolutePath()

    override fun toRealPath(path: Path, vararg linkOptions: LinkOption): Path {
        checkPath(path)
        return path.toRealPath(*linkOptions)
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption,
    ): MutableMap<String, Any> {
        checkPath(path)
        return delegate.readAttributes(path, attributes, *options)
    }
}
