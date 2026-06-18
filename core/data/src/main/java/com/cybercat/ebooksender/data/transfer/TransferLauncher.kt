package com.cybercat.ebooksender.data.transfer

interface TransferLauncher {
    fun startTransfer(requestId: String)
    fun cancelTransfer(requestId: String)
}
