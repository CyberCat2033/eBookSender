package com.cybercat.pocketbooksender.metadata

import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import javax.inject.Inject

class FilenameMetadataExtractor @Inject constructor() : MetadataExtractor {
    override suspend fun extract(sourceUri: String, displayName: String): BookMetadata {
        val title = displayName.bookTitleWithoutExtension()
        return BookMetadata(title = title.ifBlank { displayName })
    }
}
