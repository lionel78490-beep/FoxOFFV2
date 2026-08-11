import Toybox.Application;
import Toybox.Background;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

//! Point d'entrée de l'app. Marquée (:background) car référencée par le
//! système au moment de résoudre getServiceDelegate() — voir doc
//! Backgrounding du SDK ("Your application object has to be marked as
//! background so that the service delegate can be referenced").
(:background)
class FoxOffGarminApp extends Application.AppBase {

    // Minimum autorisé par Background.registerForTemporalEvent().
    private const TEMPORAL_EVENT_INTERVAL_SECONDS = 5 * 60;

    public function initialize() {
        AppBase.initialize();
    }

    public function onStart(state as Dictionary?) as Void {
        registerTemporalEventIfNeeded();
    }

    public function onStop(state as Dictionary?) as Void {
    }

    //! Idempotent : ne réenregistre pas si déjà programmé, pour ne pas
    //! perturber l'échéance en cours à chaque relance de l'app au premier
    //! plan (voir doc Backgrounding, exemple officiel).
    private function registerTemporalEventIfNeeded() as Void {
        if (Background.getTemporalEventRegisteredTime() == null) {
            try {
                Background.registerForTemporalEvent(new Time.Duration(TEMPORAL_EVENT_INTERVAL_SECONDS));
            } catch (e instanceof Background.InvalidBackgroundTimeException) {
                System.println("FoxOFF: échec enregistrement événement temporel");
            }
        }
    }

    //! Uniquement appelée au premier plan, jamais depuis le contexte
    //! background — même si la classe englobante est (:background) (requis
    //! pour que getServiceDelegate() soit résolue). Voir
    //! BackgroundTimerApp.mc (exemple officiel du SDK) pour le même
    //! contournement (:typecheck(disableBackgroundCheck)).
    (:typecheck(disableBackgroundCheck))
    public function getInitialView() as [Views] or [Views, InputDelegates] {
        var view = new FoxOffGarminView();
        return [view, new FoxOffGarminDelegate(view)];
    }

    public function getServiceDelegate() as [ServiceDelegate] {
        return [new FoxOffGarminServiceDelegate()];
    }
}
