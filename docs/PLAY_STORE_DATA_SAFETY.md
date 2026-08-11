# Formulaire "Sécurité des données" — Play Console

Aide-mémoire pour remplir le formulaire Data Safety dans Play Console
(Présence sur le store > Sécurité des données). Basé sur les permissions et
flux de données réels du code (voir [PRIVACY.md](../PRIVACY.md) pour la
version destinée aux utilisateurs). À copier dans l'interface Play
Console — je ne peux pas remplir ce formulaire à ta place (compte Google
requis).

## Q1 — Collecte de données

**"Votre application collecte-t-elle ou partage-t-elle des types de
données utilisateur requis ?"** → **Oui**

## Q2 — Type de données : Santé et forme physique

- Catégories : **Fréquence cardiaque** ("Heart rate") et **Sommeil**
  ("Sleep" — lecture seule des sessions déjà enregistrées par l'app
  compagnon de la montre, pour affiner la calibration du BPM de repos)
- Collectée ? **Oui** (les deux)
- Partagée avec des tiers ? **Non**
- Traitement éphémère uniquement (pas stockée) ? **Non** — un historique
  diagnostique (score, BPM, événements de la nuit) est conservé
  **localement sur l'appareil** (jamais transmis).
- Cette collecte est-elle obligatoire ou facultative ? **Obligatoire**
  (fonctionnalité cœur de l'app — sans BPM, aucune détection de sommeil
  n'est possible).
- Finalité : **Fonctionnalité de l'application** (App functionality).
- Chiffrée en transit ? Sans objet — aucune transmission réseau de cette
  donnée (pas de serveur FoxOFF).
- L'utilisateur peut-il demander la suppression ? **Oui** — désinstaller
  l'app, ou effacer l'historique depuis l'onglet Historique.

## Q3 — Type de données : Identifiants d'appareil ou autres

- Catégorie : **Autres identifiants** ("Device or other IDs") — l'adresse/
  le nom Bluetooth de la montre et l'empreinte de certificat TLS de la TV
  appairée.
- Collectée ? **Oui**
- Partagée avec des tiers ? **Non**
- Finalité : **Fonctionnalité de l'application** (appairage montre/TV).
- Stockage : localement sur l'appareil uniquement.

## Toutes les autres catégories du formulaire

Localisation, infos personnelles, infos financières, messages, photos/
vidéos, audio, fichiers/documents, calendrier, contacts, activité dans
l'app, navigation web, infos sur l'app/performances → **Non collectées**.

## Sécurité des données (section globale)

- Les données sont-elles chiffrées en transit ? **Sans objet** — aucune
  donnée ne quitte l'appareil vers un serveur FoxOFF (il n'en existe pas).
  La connexion à la TV locale utilise TLS, mais ce n'est pas un transfert
  vers un tiers.
- Peux-tu demander la suppression de tes données ? **Oui** — bouton
  "Effacer" dans l'onglet Historique, ou désinstallation complète.
- Respecte la politique Google Play sur les Familles ? Non applicable
  (app non destinée aux enfants — voir questionnaire de classification du
  contenu, cible "Adultes"/tout public standard selon ce que tu choisiras).

## Lien vers la politique de confidentialité

URL à renseigner dans Play Console : lien GitHub vers `PRIVACY.md` une
fois poussé sur le dépôt distant, ex.
`https://github.com/lionel78490-beep/FoxOFFV2/blob/main/PRIVACY.md`
(nécessite que le fichier soit commité et poussé — je ne l'ai pas fait
automatiquement, à confirmer avec toi).
