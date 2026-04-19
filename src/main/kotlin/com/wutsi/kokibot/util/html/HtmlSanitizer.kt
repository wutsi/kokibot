package com.wutsi.kokibot.util.html

import org.jsoup.Jsoup

class HtmlSanitizer {
    companion object {
        private val ID_CSS_BLACKLIST = listOf<String>(
            "footer",
            "comments",
            "menu-ay-side-menu-mine",
            "mashsb-container",
            "top-nav",
            "related_posts",
            "share-post",
            "navbar",
            "nav",
            "addthis_tool",
            "embedly-card",
            "sidebar",
            "rrssb-buttons", // See https://github.com/AdamPS/rrssb-plus
            "the_champ_sharing_container", // https://github.com/wp-plugins/super-socializer
            "a2a_kit", // https://www.addtoany.com/

            "jeg_share_top_container",
            "jeg_share_bottom_container",
            "jeg_post_tags",
            "jp-relatedposts",
            "truncate-read-more",
            "jnews_author_box_container",
            "jnews_related_post_container",
            "jnews_prev_next_container",
            "jnews_inline_related_post_wrapper",
            "ads-wrapper",

            "td-post-sharing",

            "banner",
            "link-cloud",
            "newsletter",
            "trending",

            // wikipedia
            "vector-page-toolbar-container",
            "vector-page-toolbar",
            "vector-column-start",
            "vector-column-end",
            "vector-body-before-content",
            "mw-editsection",
            "mw-references-wrap",
            "catlinks",
            "navigation",
            "navbox-styles",
            "infobox"
        )
    }

    fun sanitize(html: String): String {
        val doc = Jsoup.parse(html)
        ID_CSS_BLACKLIST.forEach { selector ->
            doc.select("#$selector").remove()
            doc.select(".$selector").remove()
        }

        return html
    }
}
