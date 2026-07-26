package lol.dogon.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val dateTaken: Long,
    val isVideo: Boolean,
    val bucketName: String,
    val displayName: String = "",
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val durationMs: Long = 0L
) {
    val isGif: Boolean get() = mimeType == "image/gif"
}

data class MediaGroup(
    val name: String,
    val items: List<MediaItem>
) {
    val cover: MediaItem get() = items.first()
    val count: Int get() = items.size
}

enum class MediaTypeFilter { ALL, PHOTO, VIDEO, GIF }
enum class SortBy { DATE, SIZE, DURATION, NAME }

data class FilterState(
    val type: MediaTypeFilter = MediaTypeFilter.ALL,
    val sortBy: SortBy = SortBy.DATE,
    val ascending: Boolean = false
)

object MediaRepository {

    // MediaStore'dan fotoğraf + videoları tarihe göre azalan sırayla çeker,
    // klasör (bucket), dosya adı, boyut ve mime-type bilgisiyle birlikte
    // (bu son üçü filtre/sıralama ekranı için gerekiyor).
    fun loadMediaItems(context: Context): List<MediaItem> {
        val result = mutableListOf<MediaItem>()

        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val bucket = cursor.getString(bucketCol) ?: "Diğer"
                val name = cursor.getString(nameCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: "image/*"
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                result.add(MediaItem(id, uri, date, isVideo = false, bucketName = bucket, displayName = name, sizeBytes = size, mimeType = mime))
            }
        }

        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection, null, null,
            "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val bucket = cursor.getString(bucketCol) ?: "Diğer"
                val name = cursor.getString(nameCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: "video/*"
                val dur = cursor.getLong(durCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                result.add(MediaItem(id, uri, date, isVideo = true, bucketName = bucket, displayName = name, sizeBytes = size, mimeType = mime, durationMs = dur))
            }
        }

        return result.sortedByDescending { it.dateTaken }
    }

    // Aynı klasördeki öğeleri gruplar (Albümler ve Klasörler ekranlarının ortak veri kaynağı).
    fun groupByFolder(items: List<MediaItem>): List<MediaGroup> {
        return items.groupBy { it.bucketName }
            .map { (name, groupItems) -> MediaGroup(name, groupItems) }
            .sortedByDescending { it.cover.dateTaken }
    }

    // Tür filtresi + sıralama uygular. Zaman/Albümler/Klasörler ekranlarının hepsi bunu paylaşır.
    fun applyFilter(items: List<MediaItem>, filter: FilterState): List<MediaItem> {
        val typeFiltered = when (filter.type) {
            MediaTypeFilter.ALL -> items
            MediaTypeFilter.PHOTO -> items.filter { !it.isVideo && !it.isGif }
            MediaTypeFilter.VIDEO -> items.filter { it.isVideo }
            MediaTypeFilter.GIF -> items.filter { it.isGif }
        }
        val sorted = when (filter.sortBy) {
            SortBy.DATE -> typeFiltered.sortedBy { it.dateTaken }
            SortBy.SIZE -> typeFiltered.sortedBy { it.sizeBytes }
            SortBy.DURATION -> typeFiltered.sortedBy { it.durationMs }
            SortBy.NAME -> typeFiltered.sortedBy { it.displayName.lowercase() }
        }
        return if (filter.ascending) sorted else sorted.reversed()
    }
}
