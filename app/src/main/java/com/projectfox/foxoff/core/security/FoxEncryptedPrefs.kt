package com.projectfox.foxoff.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Point d'accès UNIQUE aux SharedPreferences de l'app — chiffrées au repos
 * (clé + valeurs) via Jetpack Security, clé maître générée et stockée dans
 * l'Android Keystore matériel (jamais exportable, jamais visible même en
 * root). Remplace `context.getSharedPreferences(name, MODE_PRIVATE)`
 * partout dans l'app : toutes les données FoxOFF sont sensibles (BPM,
 * sommeil, identifiants montre/TV), pas la peine de maintenir une liste de
 * ce qui mérite le chiffrement et ce qui ne le mérite pas.
 *
 * Cache par nom de fichier : `EncryptedSharedPreferences.create()` fait un
 * vrai travail (dérivation de clé, initialisation du Keystore) à chaque
 * appel — sans cache, chaque lecture de réglage recréerait l'instance.
 *
 * `factory` est un point d'injection réservé aux tests unitaires JVM : la
 * fabrication réelle passe par l'Android Keystore matériel, absent des
 * tests JVM (app/src/test, sans Robolectric) — sans ce point d'injection,
 * chaque appel à get() y lèverait une exception. Les tests concernés
 * appellent `setTestFactory { _, _ -> FakeSharedPreferences() }` dans leur
 * setUp() (voir BackgroundServiceSettingsTest, SleepDetectionHistoryTest,
 * FoxTvSettingsTest) au lieu de mocker `Context.getSharedPreferences()`
 * comme avant ce chiffrement.
 */
object FoxEncryptedPrefs {

    private val cache = mutableMapOf<String, SharedPreferences>()

    private var factory: (Context, String) -> SharedPreferences = ::createEncrypted

    @Synchronized
    fun get(context: Context, name: String): SharedPreferences {
        return cache.getOrPut(name) { factory(context.applicationContext, name) }
    }

    private fun createEncrypted(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Réservé aux tests JVM — voir la KDoc de la classe. */
    fun setTestFactory(factory: (Context, String) -> SharedPreferences) {
        this.factory = factory
    }

    /** Réservé aux tests JVM : repart d'un cache vide entre deux tests indépendants. */
    fun resetForTests() {
        cache.clear()
        factory = ::createEncrypted
    }
}
