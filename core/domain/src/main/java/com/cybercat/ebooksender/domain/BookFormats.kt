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
 * types for formats such as FB2, wrapped books, comic archives, MOBI/AZW3, or DJVU. Broad
 * fallbacks intentionally allow some unsupported files into the picker; upload validation rejects
 * them after selection.
 */
fun supportedPickerMimeTypes(): Array<String> = arrayOf(
    "application/epub+zip",
    "application/fb2+xml",
    "application/xml",
    "text/fb2+xml",
    "text/xml",
    "application/x-fictionbook",
    "application/x-fictionbook+xml",
    "application/x-mobipocket-ebook",
    "application/x-mobi8-ebook",
    "application/vnd.amazon.ebook",
    "application/vnd.amazon.mobi8-ebook",
    "text/plain",
    "application/rtf",
    "application/x-rtf",
    "text/rtf",
    "text/richtext",
    "application/pdf",
    "application/djvu",
    "application/vnd.djvu",
    "application/x-djvu",
    "image/vnd.djvu",
    "image/x-djvu",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "application/rar",
    "application/vnd.rar",
    "application/x-rar",
    "application/x-rar-compressed",
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
