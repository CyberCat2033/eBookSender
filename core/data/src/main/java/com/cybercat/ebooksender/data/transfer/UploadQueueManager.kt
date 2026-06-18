package com.cybercat.ebooksender.data.transfer

import android.net.Uri
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import kotlinx.coroutines.flow.StateFlow

interface UploadQueueManager {
    val queue: StateFlow<List<UploadItem>>
    fun addUris(uris: List<Uri>)
    fun addPreparedItems(items: List<UploadItem>)
    fun prioritizeMetadata(itemIds: List<String>)
    fun removeItem(id: String)
    fun clearQueue()
    fun removeDownloadCacheItems(): Int
    fun updateCategory(id: String, category: BookCategory)
    fun updateDocumentsTag(id: String, tag: String)
    fun updateMangaSeries(id: String, series: String)
    fun updateQueuedMangaSeries(oldSeries: String?, series: String)
    fun updateQueue(
        deduplicate: Boolean = true,
        updateBlock: (List<UploadItem>) -> List<UploadItem>
    )
}
