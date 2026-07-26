package lol.dogon.gallery.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

// Fotoğrafları ML Kit'in on-device (cihaz üzerinde, internete çıkmadan) görsel
// etiketleme modeliyle tarar ve sonucu basit bir JSON önbelleğinde saklar.
// Böylece "Ara" ekranında "köpek", "deniz", "yemek" gibi içerik bazlı arama yapılabilir.
object TagIndexer {
    private const val PREFS_NAME = "dogon_tag_index"
    private const val KEY_TAGS = "tags_json"
    private const val CONFIDENCE_THRESHOLD = 0.6f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readAll(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_TAGS, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun writeAll(context: Context, obj: JSONObject) {
        prefs(context).edit().putString(KEY_TAGS, obj.toString()).apply()
    }

    fun getTags(context: Context, mediaId: Long): List<String> {
        val obj = readAll(context)
        val arr = obj.optJSONArray(mediaId.toString()) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun isIndexed(context: Context, mediaId: Long): Boolean =
        readAll(context).has(mediaId.toString())

    // Tüm benzersiz etiketleri döner (arama ekranındaki öneri chip'leri için).
    fun getAllKnownTags(context: Context): List<String> {
        val obj = readAll(context)
        val set = mutableSetOf<String>()
        obj.keys().forEach { key ->
            val arr = obj.optJSONArray(key)
            if (arr != null) {
                for (i in 0 until arr.length()) set.add(arr.getString(i))
            }
        }
        return set.sorted()
    }

    // Henüz etiketlenmemiş öğeleri sırayla etiketler, ilerlemeyi onProgress ile bildirir.
    // Zaten etiketlenmiş öğeleri atlar, bu yüzden ekrana her girişte baştan başlamaz.
    suspend fun indexMissing(
        context: Context,
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        val toIndex = items.filter { !isIndexed(context, it.id) }
        if (toIndex.isEmpty()) return

        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val obj = readAll(context)

        toIndex.forEachIndexed { index, item ->
            try {
                val tags = labelImage(context, item.uri, labeler)
                val arr = org.json.JSONArray()
                tags.forEach { arr.put(it) }
                obj.put(item.id.toString(), arr)
            } catch (e: Exception) {
                // Bozuk/okunamayan dosya olursa atla, indexlemeyi durdurma
                obj.put(item.id.toString(), org.json.JSONArray())
            }
            onProgress(index + 1, toIndex.size)
            // Her 20 öğede bir diske yaz, tüm işlem yarıda kesilirse de ilerleme kaybolmasın
            if (index % 20 == 0) writeAll(context, obj)
        }
        writeAll(context, obj)
    }

    private suspend fun labelImage(
        context: Context,
        uri: Uri,
        labeler: com.google.mlkit.vision.label.ImageLabeler
    ): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val labels = labeler.process(image).await()
        return labels
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
            .map { it.text }
    }
}
