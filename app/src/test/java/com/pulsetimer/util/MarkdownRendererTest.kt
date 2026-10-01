package com.pulsetimer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun rendersInlineFormattingInExistingOrder() {
        val html = MarkdownRenderer.toHtml("**bold** and *italic* with `code`")

        assertEquals(
            "<p><strong>bold</strong> and <em>italic</em> with <code>code</code></p>\n",
            html
        )
    }

    @Test
    fun escapesHtmlAndPreservesEscapedAmpersandsInLinks() {
        val html = MarkdownRenderer.toHtml(
            "[link](https://example.com/?a=1&b=2) <tag>"
        )

        assertTrue(html.contains("href=\"https://example.com/?a=1&amp;b=2\""))
        assertTrue(html.contains("&lt;tag&gt;"))
    }
}
