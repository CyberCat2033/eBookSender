package com.cybercat.ebooksender.data.update

fun nextUpdateStatusEventId(currentStatus: Any?, nextStatus: Any?, currentEventId: Long): Long =
    if (currentStatus == null && nextStatus == null) {
        currentEventId
    } else {
        currentEventId + 1L
    }
