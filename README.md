# Lexia TV

Application pour **Fire TV** (navigateur Silk), pilotée à la télécommande, avec une télécommande mobile.

| Module | Ce qu'il fait | Télécommande |
|---|---|---|
| **Salle de préparation** | Chronologie, pièces clés et « bombes » (pièce adverse ↔ pièce qui la contredit) sur grand écran | ◀ ▶ élément · ▲ ▼ section · OK agrandir |
| **Prompteur** | Plaidoirie qui défile, chronomètre par acte (rouge si dépassement) et total | OK / ⏯ lecture-pause · ▲ ▼ vitesse · ◀ ▶ acte · ⏪ ⏩ ajuster |
| **Diffusion depuis le téléphone** | On choisit une pièce sur le téléphone, elle s'affiche en plein écran sur la TV | OK / Retour pour fermer |
| **Gestion locative** | État des meublés aujourd'hui, agenda 14 jours (arrivées, départs, ménages), alerte si aucun ménage n'est prévu entre deux séjours | ▲ ▼ faire défiler |

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

## Confidentialité

Le serveur n'écoute que sur le réseau local et n'envoie rien sur Internet, mais **toute personne connectée au même Wi-Fi peut ouvrir les pages**. À utiliser sur un réseau de confiance (pas un Wi-Fi public).

## Structure

```
server.js          serveur HTTP sans dépendance + relais téléphone → TV (Server-Sent Events)
public/index.html  application TV
public/app.js      vues, navigation télécommande, réception des diffusions
public/remote.*    télécommande mobile
public/common.js   utilitaires partagés (dates, échappement HTML)
data/*.json        contenus
```

## Pistes suivantes

- Empaqueter l'app en APK (WebView) pour la lancer depuis l'écran d'accueil Fire TV sans passer par Silk.
- Afficher les PDF des pièces (rendu page par page avec pdf.js).
- Synchroniser `data/` depuis Google Drive ou Dropbox.
