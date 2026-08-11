import Toybox.Activity;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! Vue minimale au premier plan : BPM courant + statut du dernier envoi.
//! Pas annotée (:background) — n'est jamais référencée depuis le chemin
//! d'exécution en arrière-plan.
class FoxOffGarminView extends WatchUi.View {

    private var _refreshTimer as Timer.Timer?;
    private var _lastSendStatus as String = "Aucun envoi";

    public function initialize() {
        View.initialize();
    }

    public function onShow() as Void {
        _refreshTimer = new Timer.Timer();
        _refreshTimer.start(method(:onRefreshTick), 2000, true);
    }

    public function onHide() as Void {
        if (_refreshTimer != null) {
            _refreshTimer.stop();
            _refreshTimer = null;
        }
    }

    public function onRefreshTick() as Void {
        WatchUi.requestUpdate();
    }

    public function setLastSendStatus(status as String) as Void {
        _lastSendStatus = status;
        WatchUi.requestUpdate();
    }

    public function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var bpm = FoxOffGarminCommon.currentBpm();
        var bpmText = (bpm != null) ? bpm.toString() + " bpm" : "-- bpm";

        var width = dc.getWidth();
        var height = dc.getHeight();

        dc.drawText(width / 2, height / 2 - 30, Graphics.FONT_LARGE, "FoxOFF", Graphics.TEXT_JUSTIFY_CENTER);
        dc.drawText(width / 2, height / 2, Graphics.FONT_NUMBER_MEDIUM, bpmText, Graphics.TEXT_JUSTIFY_CENTER);
        dc.drawText(width / 2, height / 2 + 40, Graphics.FONT_TINY, _lastSendStatus, Graphics.TEXT_JUSTIFY_CENTER);
        dc.drawText(width / 2, height - 20, Graphics.FONT_XTINY, "Taper pour envoyer", Graphics.TEXT_JUSTIFY_CENTER);
    }
}
