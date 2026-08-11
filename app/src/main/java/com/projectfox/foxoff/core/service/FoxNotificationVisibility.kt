package com.projectfox.foxoff.core.service

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Détermine si une notification postée sur le canal FoxOFF serait
 * réellement VISIBLE par l'utilisateur. Le refus de POST_NOTIFICATIONS
 * n'empêche pas toujours techniquement un service de premier plan de
 * tourner — mais FoxOFF choisit volontairement de ne jamais faire tourner
 * une surveillance dont la notification permanente ne serait pas visible,
 * par transparence : c'est une décision produit, pas une contrainte
 * Android.
 *
 * Trois vérifications indépendantes, les trois doivent passer :
 * 1. POST_NOTIFICATIONS accordée (Android 13+ uniquement) ;
 * 2. les notifications de l'app ne sont pas coupées globalement
 *    (NotificationManagerCompat.areNotificationsEnabled()) ;
 * 3. le canal FoxOFF lui-même n'a pas été mis en importance NONE par
 *    l'utilisateur (canal pas encore créé = pas encore bloquant).
 */
object FoxNotificationVisibility {

    fun isVisible(context: Context, channelId: String): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) return false

        val manager = NotificationManagerCompat.from(context)

        if (!manager.areNotificationsEnabled()) return false

        val channel = manager.getNotificationChannel(channelId)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false

        return true
    }
}
