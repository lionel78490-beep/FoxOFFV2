import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

//! Envoi manuel au tap/bouton principal — utile pour un test immédiat, en
//! plus de l'envoi automatique périodique (FoxOffGarminServiceDelegate).
//! onSelect() couvre aussi bien les montres tactiles que celles à boutons.
class FoxOffGarminDelegate extends WatchUi.BehaviorDelegate {

    private var _view as FoxOffGarminView;

    public function initialize(view as FoxOffGarminView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    public function onSelect() as Boolean {
        var sent = FoxOffGarminCommon.sendReading(new FoxOffGarminManualSendListener(_view));
        if (!sent) {
            _view.setLastSendStatus("BPM indisponible");
        } else {
            _view.setLastSendStatus("Envoi en cours...");
        }
        return true;
    }
}

//! Écoute la fin de l'envoi manuel pour mettre à jour la vue — distinct du
//! listener background (FoxOffGarminSendListener), qui appelle
//! Background.exit() à la place.
class FoxOffGarminManualSendListener extends Communications.ConnectionListener {

    private var _view as FoxOffGarminView;

    public function initialize(view as FoxOffGarminView) {
        Communications.ConnectionListener.initialize();
        _view = view;
    }

    public function onComplete() as Void {
        _view.setLastSendStatus("Envoyé");
    }

    public function onError() as Void {
        _view.setLastSendStatus("Échec de l'envoi");
    }
}
