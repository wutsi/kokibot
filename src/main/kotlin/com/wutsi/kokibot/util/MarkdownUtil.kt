package com.wutsi.kokibot.util

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

object MarkdownUtil {
    private val flexmarkOptions = MutableDataSet()
    private val flexmarkParser = Parser.builder(flexmarkOptions).build()
    private val flexmarkRenderer = HtmlRenderer.builder(flexmarkOptions).build()

    fun escape(text: String): String {
        // These characters MUST be escaped if they are intended to be plain text
        val reservedChars = listOf(
            "_", "*", "`", "|"
        )

        var sanitized = text
        reservedChars.forEach { char ->
            sanitized = sanitized.replace(char, "\\$char")
        }
        return sanitized
    }

    /**
     * Converts a Markdown document into plain text.
     *
     * The Markdown is parsed and rendered to HTML by flexmark, then walked
     * with jsoup to keep block-level structure (paragraphs, headings, list
     * items, line breaks, horizontal rules) while stripping every formatting
     * marker (bold, italic, code spans, links, etc.).
     *
     * @param markdown the Markdown source to convert
     * @return the plain-text representation of [markdown]; empty string when
     *         the input is blank
     */
    fun toText(markdown: String): String {
        if (markdown.isBlank()) return ""

        val html = flexmarkRenderer.render(flexmarkParser.parse(markdown))
        val doc = Jsoup.parse(html)

        // Inject text-node separators so block boundaries survive in the
        // textual output produced by Element.wholeText().
        doc.select("br").forEach { it.replaceWith(TextNode("\n")) }
        doc.select("hr").forEach { it.replaceWith(TextNode("\n---\n")) }
        doc.select("li").forEach {
            it.prependChild(TextNode("- "))
            it.appendChild(TextNode("\n"))
        }
        doc.select("p, h1, h2, h3, h4, h5, h6, blockquote, pre")
            .forEach { it.appendChild(TextNode("\n\n")) }

        return doc.body().wholeText()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Splits a markdown text into chunks of at most [maxLength] characters,
     * trying to preserve the document structure by breaking on the safest
     * boundary available (paragraph > line > sentence > word > hard cut).
     *
     * Fenced code blocks (```) that span a chunk boundary are closed at the
     * end of the chunk and reopened at the beginning of the next chunk so
     * that each chunk remains a valid, self-contained markdown document.
     */
    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength > 0) { "maxLength must be > 0" }
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text
        var openFence: String? = null // e.g. "```kotlin"

        while (remaining.length > maxLength) {
            val prefix = if (openFence != null) openFence + "\n" else ""
            val suffix = "\n```"

            // First attempt: only reserve space for the prefix (fence reopen)
            var budget = maxLength - prefix.length
            if (budget <= 0) {
                // maxLength too small to wrap fences; fall back to hard cut
                chunks.add(remaining.substring(0, maxLength))
                remaining = remaining.substring(maxLength)
                continue
            }

            var cut = findSafeCut(remaining, budget)
            var body = remaining.substring(0, cut)
            var nextOpenFence = updateFenceState(openFence, body)

            // If a fence is left open, we need room for the closing suffix.
            // Re-cut with reduced budget if the chunk would overflow.
            if (nextOpenFence != null && prefix.length + body.length + suffix.length > maxLength) {
                budget = (maxLength - prefix.length - suffix.length).coerceAtLeast(1)
                cut = findSafeCut(remaining, budget)
                body = remaining.substring(0, cut)
                nextOpenFence = updateFenceState(openFence, body)
            }

            var chunk = prefix + body.trimEnd()
            if (nextOpenFence != null) {
                chunk += suffix
            }
            chunks.add(chunk)

            openFence = nextOpenFence
            remaining = remaining.substring(cut).trimStart()
        }

        if (remaining.isNotEmpty()) {
            val last = if (openFence != null) openFence + "\n" + remaining else remaining
            chunks.add(last)
        }
        return chunks
    }

    private fun findSafeCut(text: String, limit: Int): Int {
        if (text.length <= limit) return text.length
        val window = text.substring(0, limit)

        // 1. paragraph break
        window.lastIndexOf("\n\n").takeIf { it > 0 }?.let { return it + 2 }
        // 2. line break
        window.lastIndexOf('\n').takeIf { it > 0 }?.let { return it + 1 }
        // 3. sentence boundary
        listOf(". ", "! ", "? ")
            .mapNotNull {
                val i = window.lastIndexOf(it)
                if (i > 0) i + it.length else null
            }
            .maxOrNull()
            ?.let { return it }
        // 4. word boundary
        window.lastIndexOf(' ').takeIf { it > 0 }?.let { return it + 1 }
        // 5. hard cut
        return limit
    }

    private fun updateFenceState(current: String?, chunk: String): String? {
        var state = current
        val regex = Regex("(?m)^```([^\\n]*)$")
        regex.findAll(chunk).forEach { match ->
            state = if (state == null) "```" + match.groupValues[1] else null
        }
        return state
    }
}
