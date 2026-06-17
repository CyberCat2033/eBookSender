package com.cybercat.pocketbooksender.data.transfer

interface TransferLauncher {
    fun startTransfer(requestId: String)
    fun cancelTransfer(requestId: String)
}
