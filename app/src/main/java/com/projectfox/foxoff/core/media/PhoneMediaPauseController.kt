package com.projectfox.foxoff.core.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import com.projectfox.foxoff.core.logging.FoxLogger

/**
 * Met en pause la lecture active sur le téléphone lui-même (vidéo, musique
 * — YouTube, Spotify, Netflix...), en complément de la pause TV existante
 * (voir SleepPauseCoordinator.executePause()). Repose sur
 * MediaSessionManager.getActiveSessions(), qui exige que l'app dispose de
 * l'accès spécial "Accès aux notifications" (NotificationListenerService,
 * voir FoxNotificationListenerService) — best-effort comme le reste des
 * interactions montre/TV : si l'accès n'est pas accordé, ne fait
 * simplement rien, rien ne peut empirer.
 */
object PhoneMediaPauseController {

    fun hasNotificationAccess(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in enabledPackages
    }

    /** @return le nombre de lectures effectivement mises en pause. */
    fun pauseAllActiveMedia(context: Context): Int {
        if (!hasNotificationAccess(context)) return 0
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return 0
            val component = ComponentName(context, FoxNotificationListenerService::class.java)
            val sessions = manager.getActiveSessions(component)
            var pausedCount = 0
            sessions.forEach { controller ->
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                    pausedCount++
                }
            }
            FoxLogger.i("FOX-MEDIA | $pausedCount lecture(s) média téléphone mise(s) en pause")
            pausedCount
        } catch (e: SecurityException) {
            // Accès révoqué entre-temps (utilisateur retiré la permission
            // dans les paramètres système) — best-effort, pas d'erreur
            // remontée à l'utilisateur.
            FoxLogger.w("FOX-MEDIA | Accès aux notifications refusé (${e.message})")
            0
        } catch (e: Exception) {
            FoxLogger.e("FOX-MEDIA | Erreur lors de la pause média téléphone", e)
            0
        }
    }
}
