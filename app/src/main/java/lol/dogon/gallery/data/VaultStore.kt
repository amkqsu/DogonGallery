package lol.dogon.gallery.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

// PIN ve "gizlenmiş" fotoğraf/video id'lerini cihazda şifreli olarak saklar.
// Not: bu, orijinal dosyayı diskte şifrelemez; sadece ana galeriden gizleyip
// PIN arkasına koyar. Gerçek dosya şifrelemesi ileride ayrı bir adımda eklenebilir.
object VaultStore {
    private const val PREFS_NAME = "dogon_vault_secure"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_HIDDEN_IDS = "hidden_ids"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == hash(pin)

    fun resetPin(context: Context) {
        prefs(context).edit().remove(KEY_PIN_HASH).apply()
    }

    fun getHiddenIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()

    fun hideItem(context: Context, id: Long) {
        val current = getHiddenIds(context).toMutableSet()
        current.add(id.toString())
        prefs(context).edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }

    fun unhideItem(context: Context, id: Long) {
        val current = getHiddenIds(context).toMutableSet()
        current.remove(id.toString())
        prefs(context).edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }
}
