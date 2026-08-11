import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

//! Point d'entrée de l'exécution en arrière-plan (voir
//! FoxOffGarminApp.getServiceDelegate()). onTemporalEvent() se déclenche au
//! rythme enregistré dans FoxOffGarminApp (5 minutes — le minimum autorisé
//! par Background.registerForTemporalEvent(), voir doc Backgrounding).
//! Comparable en granularité à la surveillance passive Wear OS (~10-15 min
//! par lot) déjà en place côté Android — pas du temps réel, même compromis.
(:background)
class FoxOffGarminServiceDelegate extends System.ServiceDelegate {

    public function initialize() {
        ServiceDelegate.initialize();
    }

    //! Budget d'exécution de 30s max (voir doc Backgrounding) : on ne quitte
    //! (Background.exit) qu'une fois l'envoi RÉELLEMENT terminé (succès ou
    //! échec), jamais immédiatement après avoir lancé transmit() — l'envoi
    //! est asynchrone, quitter trop tôt le couperait avant d'aboutir.
    public function onTemporalEvent() as Void {
        var sent = FoxOffGarminCommon.sendReading(new FoxOffGarminSendListener());

        if (!sent) {
            // Pas de BPM disponible cette fois (capteur pas encore chaud,
            // montre retirée...) : on quitte tout de suite, rien à attendre.
            Background.exit(null);
        }
        // Sinon : FoxOffGarminSendListener.onComplete()/onError() se charge
        // d'appeler Background.exit() une fois l'envoi terminé.
    }
}

//! Écoute la fin de l'envoi déclenché par onTemporalEvent() ci-dessus, pour
//! ne quitter le contexte d'arrière-plan qu'une fois l'opération terminée.
(:background)
class FoxOffGarminSendListener extends Communications.ConnectionListener {

    public function initialize() {
        Communications.ConnectionListener.initialize();
    }

    public function onComplete() as Void {
        Background.exit(null);
    }

    public function onError() as Void {
        Background.exit(null);
    }
}
