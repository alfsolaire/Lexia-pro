# Alf TV Pro IPTV : lecteur IPTV pour Fire TV

Application Android TV / Fire TV (Kotlin, Jetpack Compose, Media3 ExoPlayer) dans l'esprit de *IPTV Smarters*.

> **Important :** Alf TV Pro IPTV est **uniquement un lecteur**. Elle ne contient et ne fournit aucune chaîne, aucun film ni aucune série.
> L'utilisateur saisit les identifiants de l'abonnement fourni par son propre fournisseur, qui doit disposer des droits de diffusion.

## Fonctionnalités

- **Connexion Xtream Codes** (serveur, utilisateur, mot de passe) ou **playlist M3U / M3U8**.
- **TV en direct** : catégories, liste des chaînes avec logos, recherche, favoris.
- **Films** (VOD) : grille d'affiches par catégorie.
- **Séries** : saisons et épisodes, enchaînement automatique de l'épisode suivant.
- **Lecteur** : MPEG-TS, HLS, MP4, MKV… avec changement de chaîne par ▲/▼ ou CH+/CH−.
- **Favoris** : touche **☰ (Menu)** de la télécommande sur une chaîne ou une affiche.
- **Guide des programmes (EPG)** : émission en cours (avec avancement) et suivante, dans la liste des chaînes et à chaque changement de chaîne (comptes Xtream).
- **Reprise de lecture** : films et épisodes reprennent là où ils ont été arrêtés (barre d'avancement sur les affiches).
- **Dernières chaînes regardées** / **Récemment vus** : catégorie dédiée en haut de la liste.
- **Plusieurs comptes** : bouton *Comptes* sur l'accueil ; favoris et historique propres à chaque compte.
- **Classement des catégories** : tri A → Z ou ordre du serveur, et ☰ sur une catégorie pour l'épingler en haut.
- Infos du compte : date d'expiration, nombre de connexions.
- Format du direct au choix (MPEG-TS ou HLS) si un serveur pose problème.
- Interface entièrement pilotable à la télécommande.

## Récupérer l'APK

Chaque push déclenche le workflow GitHub Actions **Build APK**, qui publie l'APK dans la release « latest » :

https://github.com/alfsolaire/Lexia-pro/releases/latest/download/LexiaPlayer.apk

Ce lien peut être saisi directement dans l'appli **Downloader** de la Fire TV.

Deux versions sont compilées :

| Fichier | Identifiant | Usage |
|---|---|---|
| `LexiaPlayer.apk` | `com.lexia.player` | Installation directe (Downloader), sans achat |
| `AlfTVPro-amazon.apk` | `com.alftv.pro` | Amazon Appstore : essai 7 jours puis déblocage à 4,99 € (achat intégré Amazon) |

Publication sur l'Appstore : voir [docs/amazon-listing.md](docs/amazon-listing.md) et
[docs/privacy-policy.md](docs/privacy-policy.md). Visuels dans `store-assets/`.

Signature définitive (facultative) : ajouter dans *Settings → Secrets and variables → Actions*
les secrets `KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` et `SIGNING_KEY_PASSWORD`.

Compilation locale (Android Studio ou SDK Android installé) :

```bash
./gradlew assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

## Installer sur Fire TV

1. Fire TV → *Paramètres* → *Ma Fire TV* → *Options pour les développeurs* :
   activez **Débogage ADB** et **Applications de sources inconnues** (pour l'appli Downloader).
2. Au choix :
   - **Downloader** (Appstore) : saisissez l'URL de l'APK ;
   - **ADB** depuis un ordinateur du même réseau :
     ```bash
     adb connect <IP-de-la-Fire-TV>:5555
     adb install -r app-release.apk
     ```
3. **Alf TV Pro IPTV** apparaît dans *Vos applications et chaînes*.

## Structure

```
app/src/main/java/com/lexia/player/
├── MainActivity.kt          navigation entre écrans
├── data/                    API Xtream, lecteur M3U, préférences, modèles
├── ui/                      écrans Compose (connexion, accueil, navigation, séries)
└── player/PlayerActivity.kt lecture vidéo (Media3 ExoPlayer)
```
