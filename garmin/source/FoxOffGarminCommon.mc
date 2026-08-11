import Toybox.Activity;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;

//! Logique partagée entre la vue au premier plan (envoi manuel) et le
//! service en arrière-plan (FoxOffGarminServiceDelegate). Référencée depuis
//! le chemin background : DOIT être annotée (:background), sans quoi le
//! compilateur refuse la compilation en mode background (voir
//! Core_Topics/Backgrounding.html du SDK).
//!
//! DOIT rester identique à GarminTransport.GARMIN_APP_ID (app/src/main/java/
//! com/projectfox/foxoff/core/watch/GarminTransport.kt, côté Android) et au
//! format du message y attendu (Dictionary {"bpm"=>Number,
//! "battery"=>Number}) — non vérifié sur matériel réel, voir l'avertissement
//! en tête de GarminTransport.kt.
(:background)
module FoxOffGarminCommon {

    const APP_ID = "b7e6f8a2-9c4d-4e1a-8f3b-2d5e6a7c9f10";

    //! Lecture instantanée du BPM courant — pas de session capteur à
    //! démarrer/arrêter, adapté à un contexte arrière-plan à budget
    //! d'exécution limité (30s max, voir doc Backgrounding).
    function currentBpm() as Number? {
        var info = Activity.getActivityInfo();
        return (info != null) ? info.currentHeartRate : null;
    }

    //! Construit le message et lance l'envoi. Renvoie false immédiatement
    //! si aucun BPM n'est disponible (rien envoyé). Le listener est
    //! OBLIGATOIRE côté appelant background : Background.exit() ne doit
    //! être appelé que depuis onComplete()/onError(), jamais juste après ce
    //! sendReading() (l'envoi est asynchrone).
    function sendReading(listener as Communications.ConnectionListener) as Boolean {
        var bpm = currentBpm();
        if (bpm == null) {
            return false;
        }

        var payload = {
            "bpm" => bpm,
            "battery" => System.getSystemStats().battery
        };

        Communications.transmit(payload, null, listener);
        return true;
    }
}
