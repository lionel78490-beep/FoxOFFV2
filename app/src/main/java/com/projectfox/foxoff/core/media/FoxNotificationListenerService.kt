package com.projectfox.foxoff.core.media

import android.service.notification.NotificationListenerService

/**
 * Aucune logique propre : sa seule raison d'être est de servir de point
 * d'ancrage pour l'accès spécial "Accès aux notifications" — c'est ce qui
 * autorise PhoneMediaPauseController à appeler
 * MediaSessionManager.getActiveSessions(), la seule API Android permettant
 * de découvrir et contrôler la lecture média active d'autres apps
 * (YouTube, Spotify, Netflix...). FoxOFF ne lit ni ne traite le contenu des
 * notifications elles-mêmes malgré la portée large de cette permission.
 */
class FoxNotificationListenerService : NotificationListenerService()
