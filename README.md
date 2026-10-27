# Lexia TV

Application pour **Fire TV** (navigateur Silk), pilotée à la télécommande, avec une télécommande mobile.

| Module | Ce qu'il fait | Télécommande |
|---|---|---|
| **Salle de préparation** | Chronologie, pièces clés et « bombes » (pièce adverse ↔ pièce qui la contredit) sur grand écran | ◀ ▶ élément · ▲ ▼ section · OK agrandir |
| **Prompteur** | Plaidoirie qui défile, chronomètre par acte (rouge si dépassement) et total | OK / ⏯ lecture-pause · ▲ ▼ vitesse · ◀ ▶ acte · ⏪ ⏩ ajuster |
| **Diffusion depuis le téléphone** | On choisit une pièce sur le téléphone, elle s'affiche en plein écran sur la TV | OK / Retour pour fermer |
| **Gestion locative** | État des meublés aujourd'hui, agenda 14 jours (arrivées, départs, ménages), alerte si aucun ménage n'est prévu entre deux séjours | ▲ ▼ faire défiler |
| **Quiz Lisieux** | 20 questions de culture générale sur Lisieux et le Pays d'Auge, 10 par partie, de 1 à 6 joueurs chacun son tour | ◀ ▶ ▲ ▼ choisir · OK valider |
| **Écran d'accueil des voyageurs** | Sur la Fire TV d'un logement loué : « Bienvenue, Roselyne », météo, agenda, actus, photos, température du logement, guide d'accueil, urgences, quiz | OK guide · ▲ photo seule · ◀ ▶ photos |

## Démarrer

Il faut un ordinateur (PC, Mac, Raspberry Pi…) sur le **même réseau Wi-Fi** que la Fire TV, avec Node.js 18 ou plus.

```bash
npm start          # ou : node server.js   (port modifiable : PORT=9000 node server.js)
```

Le serveur affiche les adresses à utiliser :

- **Sur la Fire TV** : ouvrir *Silk* → `http://<ip-du-pc>:8080/`
  (astuce : ajouter la page aux favoris de Silk pour la retrouver en un clic).
- **Sur le téléphone** : `http://<ip-du-pc>:8080/remote.html`
  - onglets *Pièces / Bombes / Chronologie* : toucher un élément l'affiche sur la TV ;
  - onglet *Télécommande* : croix directionnelle, lecture/pause et accès direct aux modules (pratique pour piloter le prompteur depuis la poche).

Le point vert en haut à droite de la TV indique que la liaison avec le téléphone est active.

## Mettre ses propres données

Tout est dans `data/` (les exemples fournis sont fictifs) :

- `dossier.json` : `chronologie`, `pieces` (`camp` = `demandeur` ou `adverse`, `extrait`, `note`, `image` facultative placée dans `data/`) et `bombes` (`adverse` / `realite` avec pièce + citation, `phrase` à prononcer).
- `plaidoirie.json` : `actes` avec `titre`, `minutes` (durée cible) et `texte`. Dans le texte : paragraphes séparés par une ligne vide, `**mot**` pour souligner, `[silence 3s]` pour marquer un silence.
- `locatif.json` : `meubles`, `reservations`, `menages`. Les dates acceptent `AAAA-MM-JJ` ou un décalage relatif à aujourd'hui (`"+2"`, `"-1"`, `"0"`).

Les fichiers sont relus à chaque chargement de la page : modifier le JSON puis recharger la TV suffit.
Pour un vrai dossier, le dossier `data/prive/` est ignoré par Git.

## Écran d'accueil des voyageurs (Airbnb, Booking…)

**Rien à installer dans le logement** : la Fire TV ouvre une adresse sur votre serveur, `https://<serveur>/accueil/<clé>/`, avec une **clé secrète par logement**.

Ce que voit le voyageur :

- **« Bienvenue, Roselyne »** (le prénom seulement), son logement et sa date de départ. Le jour du départ : « Bon retour, Roselyne — départ avant 11:00 ». Entre deux séjours : « Bienvenue ». En anglais si le séjour est marqué `"langue": "en"`.
- La **météo** de la ville (Open-Meteo, sans clé ni compte) : maintenant et les 4 jours suivants.
- La **température du logement** (mesure, consigne, humidité, eau chaude), fournie par Courte durée. Masquée si la mesure a plus de 3 h.
- **Cette semaine** : les événements d'un agenda Google (adresse iCal).
- **Infos pratiques** : Wi-Fi, heure de départ, contact, consignes.
- Les **actus** (flux RSS) qui défilent en bas.
- Un **diaporama de photos** en fond.
- Avec **OK** : le **guide d'accueil** (le même que dans Courte durée), plus les numéros d'**urgence** et le **quiz** sur Lisieux.

### D'où viennent les données

| Donnée | Source | Réglage |
|---|---|---|
| Prénom, dates, température, guide, Wi-Fi | **Courte durée**, par sa « fiche TV » | `source` : voir **[docs/contrat-courte-duree.md](docs/contrat-courte-duree.md)**, à transmettre à l'instance qui code Courte durée |
| Météo | Open-Meteo | `latitude`, `longitude` |
| Agenda | Google Agenda → Paramètres de l'agenda → *Adresse publique (ou secrète) au format iCal* | `agenda` |
| Actus | Flux RSS | `actus` |
| Photos | Un dossier du serveur, synchronisé avec Google Drive ou Dropbox (par exemple avec `rclone sync` toutes les heures) | `photos.dossier` |

En attendant la fiche de Courte durée, tout peut aussi s'écrire à la main dans la configuration (`wifi`, `contact`, `consignes`, `guide`, `sejours.fichier`).

### Configuration

Fichier `data/prive/accueil.json` sur le serveur (le dossier `data/prive/` n'est jamais publié dans Git). Modèle : `data/accueil.exemple.json`.

```json
{
  "actus": [{ "nom": "franceinfo", "url": "https://www.francetvinfo.fr/titres.rss" }],
  "agenda": [{ "nom": "Lisieux", "ical": "https://calendar.google.com/calendar/ical/…/basic.ics" }],
  "photos": { "dossier": "/srv/photos-tv", "intervalle": 12 },
  "logements": {
    "lisieux1": {
      "cle": "…",
      "nom": "Lisieux Appt N°1", "ville": "Lisieux", "latitude": 49.146, "longitude": 0.226,
      "source": { "url": "https://…/api/tv/lisieux1", "entetes": { "Authorization": "Bearer …" } }
    }
  }
}
```

- Une clé par logement : `npm run cle` en fabrique une (24 caractères au hasard). Changer la clé coupe l'ancienne adresse.
- Ce qui est dans un logement (photos, agenda, actus…) l'emporte sur les réglages communs.
- Le quiz est proposé automatiquement si la ville est Lisieux (`"quiz": null` pour l'enlever).

### Sur le serveur

```bash
LEXIA_MODE=accueil PORT=8090 HOST=127.0.0.1 node server.js     # ou : npm run accueil
```

En **mode accueil**, le serveur ne sert **que** les écrans d'accueil : le dossier prud'hommes, le prompteur, la gestion locative et la télécommande ne sont plus accessibles. Une mauvaise clé renvoie une erreur 404. Seul le prénom du voyageur en cours sort du serveur, jamais son nom ni les autres séjours. Le fichier d'exemple n'est jamais utilisé dans ce mode.

Derrière le serveur web existant (HTTPS), par exemple avec nginx :

```nginx
location /accueil/ {
    proxy_pass http://127.0.0.1:8090;
}
```

Et pour qu'il redémarre tout seul, un service systemd `/etc/systemd/system/lexia-accueil.service` :

```ini
[Service]
WorkingDirectory=/srv/lexia-tv
Environment=LEXIA_MODE=accueil PORT=8090 HOST=127.0.0.1
ExecStart=/usr/bin/node server.js
Restart=always
User=www-data

[Install]
WantedBy=multi-user.target
```

### Sur la Fire TV du logement

1. Installer **Silk** (le navigateur d'Amazon) depuis l'Appstore.
2. Ouvrir `https://<serveur>/accueil/<clé>/`, l'ajouter aux **favoris**, puis passer en plein écran.
3. Paramètres → Affichage et sons → **Économiseur d'écran** : le désactiver ou le retarder.

Silk ne s'ouvre pas tout seul au démarrage de la Fire TV. Pour ça, il faudra une petite application (APK) qui ouvre directement l'écran d'accueil : voir les pistes plus bas.

## Confidentialité

En mode normal, le serveur est fait pour le réseau local : **toute personne connectée au même Wi-Fi peut ouvrir les pages** (dossier, plaidoirie…). À utiliser sur un réseau de confiance, jamais exposé sur Internet. Pour Internet, c'est le **mode accueil** (ci-dessus).

## Structure

```
server.js            serveur HTTP sans dépendance + relais téléphone → TV (Server-Sent Events) + mode accueil
lib/accueil.js       écran d'accueil : clés des logements, voyageur en cours, fiche Courte durée, météo, agenda, actus, photos
lib/ical.js          lecture des agendas iCal (Google), répétitions comprises
lib/rss.js           lecture des flux d'actus
public/index.html    application TV (app.js, style.css)
public/accueil.*     écran d'accueil des voyageurs
public/quiz.*        quiz à la télécommande ; questions dans public/quiz-<nom>.json
public/remote.*      télécommande mobile
public/common.js     utilitaires partagés (dates, échappement HTML)
data/*.json          contenus d'exemple ; data/prive/ pour les vrais (non publié)
docs/                consignes pour Courte durée
```

## Pistes suivantes

- Empaqueter en APK (WebView) pour lancer l'écran d'accueil au démarrage de la Fire TV du logement, sans passer par Silk.
- Un message ponctuel du loueur sur la télé du logement (champ `message` de la fiche Courte durée).
- Le quiz en anglais pour les voyageurs étrangers ; d'autres quiz (jurisprudence…) : un fichier `public/quiz-<nom>.json` de plus.
- Afficher les PDF des pièces (rendu page par page avec pdf.js).
- Synchroniser `data/` depuis Google Drive ou Dropbox.
