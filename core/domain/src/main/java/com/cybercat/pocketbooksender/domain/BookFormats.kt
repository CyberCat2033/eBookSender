package com.cybercat.pocketbooksender.domain

/** Расширения документов. */
val DocumentFileExtensions: Set<String> = setOf(
    "pdf",
    "djvu",
    "doc",
    "docx"
)

/** Поддерживаемые расширения чтения, кроме manga-архивов. */
val BookFileExtensions: Set<String> = setOf(
    "epub",
    "fb2",
    "mobi",
    "azw3",
    "txt",
    "rtf"
) + DocumentFileExtensions

/** Расширения manga-архивов. */
val MangaArchiveExtensions: Set<String> = setOf("cbz", "cbr")

/** Все поддерживаемые расширения (книги + manga). */
val AllSupportedExtensions: Set<String> = BookFileExtensions + MangaArchiveExtensions
