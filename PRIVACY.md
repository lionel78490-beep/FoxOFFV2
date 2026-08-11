# Politique de confidentialité — FoxOFF

**Dernière mise à jour : 10 août 2026**

FoxOFF est une application personnelle qui détecte l'endormissement de
l'utilisateur (fréquence cardiaque et mouvement, mesurés par une montre
connectée) pour mettre automatiquement en pause la lecture sur une TV
Android/Google TV du même réseau local.

## Résumé

- **Aucune donnée ne quitte votre appareil.** FoxOFF ne dispose d'aucun
  serveur, d'aucun compte utilisateur, d'aucun service cloud. Tout le
  traitement (détection de sommeil, historique) se fait localement, sur
  votre téléphone.
- **Aucune publicité, aucun traceur, aucune revente de données.**
- **Aucune donnée n'est partagée avec un tiers.**

## Données collectées et leur usage

### Fréquence cardiaque et mouvement (données de santé)
Lues depuis votre montre connectée (Wear OS ou Garmin) pendant que la
surveillance est active. Utilisées uniquement pour calculer, sur votre
téléphone, une probabilité d'endormissement. Ces données ne sont **jamais**
transmises hors de votre téléphone — ni vers un serveur FoxOFF (il n'en
existe pas), ni vers un tiers.

Avec votre autorisation explicite, FoxOFF peut aussi lire votre historique
de fréquence cardiaque et vos sessions de sommeil déjà présents sur
l'appareil via **Health Connect** (alimenté par Samsung Health ou Garmin
Connect selon votre montre), en lecture seule, pour calculer une fréquence
de repos personnalisée (en priorité à partir du BPM mesuré pendant vos
sessions de sommeil déjà enregistrées, sinon à partir de votre fréquence
cardiaque de repos générale). FoxOFF ne demande et n'utilise **aucune**
permission d'écriture sur Health Connect.

### Connexion à la TV
FoxOFF découvre et contrôle votre TV Android/Google TV **sur le réseau
local uniquement** (protocole Android TV Remote v2, connexion chiffrée
directe entre votre téléphone et la TV). Aucune commande, aucune donnée
liée à cette connexion ne transite par un serveur externe.

### Historique de diagnostic
Un journal des événements de la nuit (score de sommeil, mouvements
détectés, connexions TV/montre) est conservé localement sur votre
téléphone, pour vous permettre de consulter le fonctionnement de l'app.
Ce journal reste sur l'appareil ; vous pouvez l'effacer à tout moment
depuis l'onglet Historique de l'application.

## Permissions demandées et pourquoi

| Permission | Usage |
|---|---|
| Fréquence cardiaque (capteurs corporels / Health Connect) | Détection de sommeil |
| Bluetooth | Communication avec la montre connectée |
| Réseau local (Wi-Fi, découverte réseau) | Découverte et contrôle de la TV |
| Notifications | Alerte avant la pause automatique de la TV (annulable) |
| Service de premier plan | Maintenir la surveillance active pendant la nuit |
| Démarrage automatique (boot) | Reprendre la surveillance après un redémarrage du téléphone, si activée |

## Conservation et suppression des données

Toutes les données (réglages, historique, appareils appairés) sont
stockées localement sur votre téléphone. Désinstaller FoxOFF supprime
définitivement toutes ces données. Vous pouvez aussi effacer l'historique
de diagnostic à tout moment depuis l'application, sans désinstaller.

## Testeurs

FoxOFF est actuellement distribué uniquement via un programme de test
restreint (test interne Google Play, sur invitation) — l'application n'est
pas disponible publiquement.

## Contact

Pour toute question sur cette politique de confidentialité :
lionel78490@gmail.com
