package com.cybercat.ebooksender.data.manga

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ComxFormEncodingTest {

    @Test
    fun buildComxLoginPostBodyPreparesCorrectBodyWithRememberMe() {
        val loginName = "user"
        val loginPassword = "password"
        val doNotRemember = false

        val expectedString = "login_name=user&login_password=password&login=submit"
        val expectedBytes = expectedString.toByteArray(Charsets.UTF_8)

        val actualBytes = buildComxLoginPostBody(loginName, loginPassword, doNotRemember)
        assertArrayEquals(expectedBytes, actualBytes)
    }

    @Test
    fun buildComxLoginPostBodyPreparesCorrectBodyWithDoNotRemember() {
        val loginName = "user"
        val loginPassword = "password"
        val doNotRemember = true

        val expectedString = "login_name=user&login_password=password&login_not_save=1&login=submit"
        val expectedBytes = expectedString.toByteArray(Charsets.UTF_8)

        val actualBytes = buildComxLoginPostBody(loginName, loginPassword, doNotRemember)
        assertArrayEquals(expectedBytes, actualBytes)
    }

    @Test
    fun toFormEncodedUtf8BodyEscapesSpecialCharacters() {
        val fields = listOf(
            "name" to "John Doe",
            "email" to "john+doe@example.com",
            "cyrillic" to "тест"
        )

        val expectedString = "name=John+Doe&email=john%2Bdoe%40example.com" +
            "&cyrillic=%D1%82%D0%B5%D1%81%D1%82"
        val expectedBytes = expectedString.toByteArray(Charsets.UTF_8)

        val actualBytes = fields.toFormEncodedUtf8Body()
        assertArrayEquals(expectedBytes, actualBytes)
    }

    @Test
    fun formEncodeEscapesCorrectly() {
        assertEquals("hello+world", "hello world".formEncode())
        assertEquals("%2Fpath%2Fto%2Fresource", "/path/to/resource".formEncode())
    }
}
