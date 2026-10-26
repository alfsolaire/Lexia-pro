# Lexia Player : lecteur IPTV pour Fire TV

Application Android TV / Fire TV (Kotlin, Jetpack Compose, Media3 ExoPlayer) dans l'esprit de *IPTV Smarters*.

> **Important :** Lexia Player est **uniquement un lecteur**. Elle ne contient et ne fournit aucune chaîne, aucun film ni aucune série.
> L'utilisateur saisit les identifiants de l'abonnement fourni par son propre fournisseur, qui doit disposer des droits de diffusion.

## Fonctionnalités

- **Connexion Xtream Codes** (serveur, utilisateur, mot de passe) ou **playlist M3U / M3U8**.
- **TV en direct** : catégories, liste des chaînes avec logos, recherche, favoris.
- **Films** (VOD) : grille d'affiches par catégorie.
- **Séries** : saisons et épisodes, enchaînement automatique de l'épisode suivant.
- **Lecteur** : MPEG-TS, HLS, MP4, MKV… avec changement de chaîne par ▲/▼ ou CH+/CH−.
- **Favoris** : touche **☰ (Menu)** de la télécommande sur une chaîne ou une affiche.
- Infos du compte : date d'expiration, nombre de connexions.
- Format du direct au choix (MPEG-TS ou HLS) si un serveur pose problème.
- Interface entièrement pilotable à la télécommande.

## Récupérer l'APK

Chaque push déclenche le workflow GitHub Actions **Build APK**, qui publie l'APK dans la release « latest » :

https://github.com/alfsolaire/Lexia-pro/releases/latest/download/LexiaPlayer.apk

Ce lien peut être saisi directement dans l'appli **Downloader** de la Fire TV.

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
3. L'application apparaît dans *Vos applications et chaînes*.

## Structure

```
app/src/main/java/com/lexia/player/
├── MainActivity.kt          navigation entre écrans
├── data/                    API Xtream, lecteur M3U, préférences, modèles
├── ui/                      écrans Compose (connexion, accueil, navigation, séries)
└── player/PlayerActivity.kt lecture vidéo (Media3 ExoPlayer)
```
