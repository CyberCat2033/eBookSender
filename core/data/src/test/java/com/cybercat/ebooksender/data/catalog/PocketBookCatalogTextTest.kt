package com.cybercat.ebooksender.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class PocketBookCatalogTextTest {

    @Test
    fun cleanCatalogTextRemovesServiceCharactersAndNormalizesSpaces() {
        val dirtyText = "\u202EВысоцкий\u200B\u0000  Владимир\n\tСеменович\u2069"

        assertEquals("Высоцкий Владимир Семенович", dirtyText.cleanCatalogText())
    }
}
