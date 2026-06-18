package com.cybercat.ebooksender.metadata

import com.cybercat.ebooksender.domain.bookTitleWithoutExtension
import javax.inject.Inject

class FilenameMetadataExtractor @Inject constructor() : MetadataExtractor {
    override suspend fun extract(sourceUri: String, displayName: String): BookMetadata {
        val title = displayName.bookTitleWithoutExtension()
        return BookMetadata(title = title.ifBlank { displayName })
    }
}
