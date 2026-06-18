package com.cybercat.ebooksender.domain

object NaturalSort {
    fun compare(left: String, right: String): Int {
        var ia = 0
        var ib = 0
        val nza = left.length
        val nzb = right.length

        while (ia < nza && ib < nzb) {
            val ca = left[ia]
            val cb = right[ib]
            val isDigitA = ca.isDigit()
            val isDigitB = cb.isDigit()

            if (isDigitA && isDigitB) {
                // Skip leading zeros
                var startA = ia
                while (startA < nza && left[startA] == '0') startA++
                var endA = startA
                while (endA < nza && left[endA].isDigit()) endA++

                var startB = ib
                while (startB < nzb && right[startB] == '0') startB++
                var endB = startB
                while (endB < nzb && right[endB].isDigit()) endB++

                val lenA = endA - startA
                val lenB = endB - startB

                if (lenA != lenB) {
                    return lenA.compareTo(lenB)
                }

                // If lengths are equal, compare digit by digit
                var ptrA = startA
                var ptrB = startB
                var valueDiff = 0
                while (ptrA < endA) {
                    val da = left[ptrA]
                    val db = right[ptrB]
                    if (da != db && valueDiff == 0) {
                        valueDiff = da.compareTo(db)
                    }
                    ptrA++
                    ptrB++
                }
                if (valueDiff != 0) return valueDiff

                // If values are equal, compare lengths including leading zeros (shorter first)
                val totalLenA = endA - ia
                val totalLenB = endB - ib
                if (totalLenA != totalLenB) {
                    return totalLenA.compareTo(totalLenB)
                }

                ia = endA
                ib = endB
            } else if (isDigitA != isDigitB) {
                return if (isDigitA) -1 else 1
            } else {
                val la = ca.lowercaseChar()
                val lb = cb.lowercaseChar()
                if (la != lb) return la.compareTo(lb)
                ia++
                ib++
            }
        }

        return (nza - ia).compareTo(nzb - ib)
    }

    fun <T> by(selector: (T) -> String): Comparator<T> =
        Comparator { left, right -> compare(selector(left), selector(right)) }
}
