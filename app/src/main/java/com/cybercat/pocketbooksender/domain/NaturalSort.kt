package com.cybercat.pocketbooksender.domain

object NaturalSort {
    fun compare(left: String, right: String): Int {
        val leftTokens = left.tokenizeNatural()
        val rightTokens = right.tokenizeNatural()
        val max = minOf(leftTokens.size, rightTokens.size)

        for (index in 0 until max) {
            val leftToken = leftTokens[index]
            val rightToken = rightTokens[index]
            val result = leftToken.compareTo(rightToken)
            if (result != 0) return result
        }

        return leftTokens.size.compareTo(rightTokens.size)
    }

    fun <T> by(selector: (T) -> String): Comparator<T> =
        Comparator { left, right -> compare(selector(left), selector(right)) }
}

private sealed class NaturalToken : Comparable<NaturalToken> {
    data class Text(val value: String) : NaturalToken()
    data class Number(val raw: String) : NaturalToken()

    override fun compareTo(other: NaturalToken): Int {
        return when {
            this is Number && other is Number -> compareNumbers(raw, other.raw)
            this is Text && other is Text -> value.compareTo(other.value, ignoreCase = true)
            this is Number -> -1
            else -> 1
        }
    }

    private fun compareNumbers(left: String, right: String): Int {
        val normalizedLeft = left.trimStart('0').ifBlank { "0" }
        val normalizedRight = right.trimStart('0').ifBlank { "0" }
        val lengthCompare = normalizedLeft.length.compareTo(normalizedRight.length)
        if (lengthCompare != 0) return lengthCompare

        val valueCompare = normalizedLeft.compareTo(normalizedRight)
        if (valueCompare != 0) return valueCompare

        return left.length.compareTo(right.length)
    }
}

private fun String.tokenizeNatural(): List<NaturalToken> {
    if (isEmpty()) return emptyList()

    val tokens = mutableListOf<NaturalToken>()
    var index = 0

    while (index < length) {
        val start = index
        val isDigitRun = this[index].isDigit()

        while (index < length && this[index].isDigit() == isDigitRun) {
            index++
        }

        val part = substring(start, index)
        tokens += if (isDigitRun) {
            NaturalToken.Number(part)
        } else {
            NaturalToken.Text(part)
        }
    }

    return tokens
}
