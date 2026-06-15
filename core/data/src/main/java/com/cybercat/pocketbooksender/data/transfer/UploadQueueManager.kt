package com.cybercat.pocketbooksender.data.transfer

import android.net.Uri
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import kotlinx.coroutines.flow.StateFlow

interface UploadQueueManager {
    val queue: StateFlow<List<UploadItem>>
    fun addUris(uris: List<Uri>)
    fun addPreparedItems(items: List<UploadItem>)
    fun removeItem(id: String)
    fun clearQueue()
    fun updateCategory(id: String, category: BookCategory)
    fun updateDocumentsTag(id: String, tag: String)
    fun updateMangaSeries(id: String, series: String)
    fun updateQueuedMangaSeries(oldSeries: String?, series: String)
    fun updateQueue(updateBlock: (List<UploadItem>) -> List<UploadItem>)
}
