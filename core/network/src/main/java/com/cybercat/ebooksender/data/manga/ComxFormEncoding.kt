package com.cybercat.ebooksender.data.manga

import java.net.URLEncoder

fun buildComxLoginPostBody(
    loginName: String,
    loginPassword: String,
    doNotRemember: Boolean,
): ByteArray {
    val fields = buildList {
        add("login_name" to loginName)
        add("login_password" to loginPassword)
        if (doNotRemember) add("login_not_save" to "1")
        add("login" to "submit")
    }
    return fields.toFormEncodedUtf8Body()
}

internal fun Iterable<Pair<String, String>>.toFormEncodedUtf8Body(): ByteArray =
    joinToString("&") { (key, value) ->
        "${key.formEncode()}=${value.formEncode()}"
    }.toByteArray(Charsets.UTF_8)

internal fun String.formEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
