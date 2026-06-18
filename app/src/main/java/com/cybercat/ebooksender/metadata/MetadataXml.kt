package com.cybercat.ebooksender.metadata

import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

internal fun newMetadataXmlParser(input: InputStream): XmlPullParser =
    XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(input, null)
    }

internal fun XmlPullParser.nextTextSafe(): String =
    runCatching { nextText().trim() }.getOrDefault("")

internal fun XmlPullParser.attributeValueByLocalName(
    localName: String,
    namespace: String? = null
): String? {
    if (namespace != null) {
        getAttributeValue(namespace, localName)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }

    getAttributeValue(null, localName)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    for (index in 0 until attributeCount) {
        val attributeName = getAttributeName(index) ?: continue
        if (attributeName.substringAfter(':') == localName) {
            return getAttributeValue(index)?.takeIf { it.isNotBlank() }
        }
    }

    return null
}
