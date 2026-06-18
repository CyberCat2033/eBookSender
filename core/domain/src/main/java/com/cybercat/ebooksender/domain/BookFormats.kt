package com.cybercat.ebooksender.domain

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

/**
 * Best-effort MIME filter for Android document pickers.
 *
 * Extension-based validation remains authoritative because many SAF providers report generic MIME
 * types for formats such as wrapped books, comic archives, MOBI/AZW3, or DJVU.
 */
fun supportedPickerMimeTypes(): Array<String> = arrayOf(
    "application/epub+zip",
    "application/fb2+xml",
    "application/x-fictionbook+xml",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "text/plain",
    "application/rtf",
    "text/rtf",
    "application/pdf",
    "image/vnd.djvu",
    "image/x-djvu",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "application/vnd.rar",
    "application/x-rar-compressed",
    "application/zip",
    "application/x-zip-compressed"
)
