package com.cybercat.pocketbooksender.domain

/** Расширения электронных книг (без manga-архивов). */
val BookFileExtensions: Set<String> = setOf(
    "epub",
    "fb2",
    "mobi",
    "azw3",
    "pdf",
    "djvu",
    "txt",
    "rtf",
)

/** Расширения manga-архивов. */
val MangaArchiveExtensions: Set<String> = setOf("cbz", "cbr")

/** Все поддерживаемые расширения (книги + manga). */
val AllSupportedExtensions: Set<String> = BookFileExtensions + MangaArchiveExtensions
