package com.cybercat.pocketbooksender.data.opds

data class OpdsCatalog(
    val title: String,
    val entries: List<OpdsEntry>,
    val links: List<OpdsLink> = emptyList(),
)

data class OpdsEntry(
    val id: String?,
    val title: String,
    val authors: List<String>,
    val summary: String?,
    val coverHref: String?,
    val navigation: List<OpdsLink>,
    val acquisitions: List<OpdsAcquisition>,
)

data class OpdsAcquisition(
    val href: String,
    val type: String?,
    val title: String?,
)

data class OpdsLink(
    val href: String,
    val rel: String?,
    val type: String?,
    val title: String?,
)

data class OpdsSource(
    val id: String,
    val title: String,
    val url: String,
)

data class OpdsSearchDescription(
    val templates: List<OpdsSearchTemplate>,
) {
    val bestTemplate: String? =
        templates.firstOrNull { template ->
            template.type.orEmpty().contains("profile=opds-catalog") ||
                template.type.orEmpty().contains("application/atom+xml")
        }?.template ?: templates.firstOrNull()?.template
}

data class OpdsSearchTemplate(
    val template: String,
    val type: String?,
)
