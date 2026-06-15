package com.cybercat.pocketbooksender.data.opds

import java.io.InputStream
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class OpdsParser @Inject constructor() {
    fun parse(input: InputStream): OpdsCatalog {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)

        var catalogTitle = "Catalog"
        val entries = mutableListOf<OpdsEntry>()
        val links = mutableListOf<OpdsLink>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "title" -> if (catalogTitle == "Catalog") catalogTitle = parser.nextTextSafe()
                "link" -> parser.readLink()?.let(links::add)
                "entry" -> entries += parser.readEntry()
            }
        }

        return OpdsCatalog(
            title = catalogTitle,
            entries = entries,
            links = links,
        )
    }

    fun parseOpenSearch(input: InputStream): OpdsSearchDescription {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)

        val templates = mutableListOf<OpdsSearchTemplate>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "Url") continue

            val template = parser.getAttributeValue(null, "template")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            templates += OpdsSearchTemplate(
                template = template,
                type = parser.getAttributeValue(null, "type"),
            )
        }

        return OpdsSearchDescription(templates = templates)
    }

    private fun XmlPullParser.readEntry(): OpdsEntry {
        var id: String? = null
        var title = "Untitled"
        var summary: String? = null
        var currentAuthor: String? = null
        val authors = mutableListOf<String>()
        var coverHref: String? = null
        val navigation = mutableListOf<OpdsLink>()
        val acquisitions = mutableListOf<OpdsAcquisition>()

        while (next() != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && name == "entry") break
            if (eventType != XmlPullParser.START_TAG) continue

            when (name) {
                "id" -> id = nextTextSafe()
                "title" -> title = nextTextSafe()
                "summary", "content" -> summary = nextTextSafe()
                "name" -> currentAuthor = nextTextSafe()
                "link" -> {
                    val link = readLink() ?: continue
                    val rel = link.rel.orEmpty()

                    if (rel.contains("image")) {
                        coverHref = link.href
                    }
                    if (rel.contains("acquisition")) {
                        acquisitions += OpdsAcquisition(
                            href = link.href,
                            type = link.type,
                            title = link.title,
                        )
                    } else if (link.isNavigation()) {
                        navigation += link
                    }
                }
            }

            currentAuthor?.let {
                authors += it
                currentAuthor = null
            }
        }

        return OpdsEntry(
            id = id,
            title = title,
            authors = authors.distinct(),
            summary = summary,
            coverHref = coverHref,
            navigation = navigation,
            acquisitions = acquisitions,
        )
    }

    private fun XmlPullParser.readLink(): OpdsLink? {
        val href = getAttributeValue(null, "href")?.takeIf { it.isNotBlank() } ?: return null
        return OpdsLink(
            href = href,
            rel = getAttributeValue(null, "rel"),
            type = getAttributeValue(null, "type"),
            title = getAttributeValue(null, "title"),
        )
    }

    private fun XmlPullParser.nextTextSafe(): String =
        runCatching { nextText().trim() }.getOrDefault("")

    private fun OpdsLink.isNavigation(): Boolean {
        val relValue = rel.orEmpty()
        val typeValue = type.orEmpty()
        return relValue == "subsection" ||
            relValue == "related" ||
            relValue.startsWith("http://opds-spec.org/sort/") ||
            typeValue.contains("profile=opds-catalog") ||
            typeValue.contains("application/atom+xml")
    }
}
