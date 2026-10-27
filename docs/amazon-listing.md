# Fiche Amazon Appstore – Alf TV Pro IPTV

## Informations générales
- **Titre** : Alf TV Pro IPTV
- **Catégorie** : Lecteurs vidéo (Video Players)
- **Prix de l'application** : Gratuit (avec achat intégré)
- **Achats intégrés** (essai gratuit de 7 jours géré par l'application, puis) :
  - **Abonnement annuel** : type *Subscription*, SKU parent `com.alftv.pro.subscription`,
    durée (*term*) **1 an** avec le SKU `com.alftv.pro.yearly`, prix **4,99 €**
  - **Accès à vie** : type *Entitlement*, SKU `com.alftv.pro.unlock`, titre « Accès à vie », prix **19,99 €**
- **Appareils** : Fire TV uniquement (toutes générations récentes)
- **Politique de confidentialité** : lien vers `docs/privacy-policy.md` (voir README)

## Description courte (FR)
Lecteur IPTV rapide et élégant pour Fire TV : Xtream Codes et M3U, guide des programmes, films, séries et reprise de lecture.

## Description longue (FR)
Alf TV Pro IPTV est un lecteur multimédia conçu pour la télécommande Fire TV.

Connectez votre propre abonnement (Xtream Codes ou playlist M3U) et profitez :
• de la TV en direct par catégories, avec logos et recherche ;
• du guide des programmes : émission en cours et suivante ;
• des films et séries avec affiches, saisons et épisodes ;
• de la reprise de lecture là où vous vous étiez arrêté ;
• des favoris, des dernières chaînes regardées et des catégories épinglées ;
• de plusieurs comptes, chacun avec ses favoris et son historique ;
• d'un changement de chaîne instantané avec les flèches de la télécommande.

Connexion rapide par QR code : saisissez votre abonnement depuis votre téléphone.
Pause du direct et reprise en différé.

Essai gratuit de 7 jours, puis abonnement annuel ou accès à vie en un seul paiement.

IMPORTANT : Alf TV Pro IPTV est uniquement un lecteur. Il ne contient et ne fournit aucune chaîne,
aucun film ni aucune série. Vous devez disposer d'un abonnement auprès d'un fournisseur de contenus
autorisé. L'éditeur n'est affilié à aucun fournisseur de contenus et ne cautionne pas la diffusion
de contenus sans l'autorisation de leurs ayants droit.

## Short description (EN)
Fast, elegant IPTV player for Fire TV: Xtream Codes and M3U, TV guide, movies, series and resume playback.

## Long description (EN)
Alf TV Pro IPTV is a media player designed for the Fire TV remote.

Connect your own subscription (Xtream Codes or M3U playlist) and enjoy live TV by category with logos
and search, a TV guide (now and next), movies and series, resume playback, favorites, recently watched
channels, pinned categories, multiple accounts and instant channel switching.

Quick QR code login from your phone, and live TV pause.

7-day free trial, then a yearly subscription or a one-time lifetime unlock.

IMPORTANT: Alf TV Pro IPTV is a player only. It does not include or provide any channels, movies or series.
You need a subscription from an authorized content provider. The publisher is not affiliated with any
content provider and does not endorse streaming content without the rights holders' permission.

## Visuels (dossier `store-assets/`)
| Fichier | Usage dans la console Amazon |
|---|---|
| `icon-512.png` | Icône (512 × 512) |
| `icon-114.png` | Petite icône (114 × 114) |
| `firetv-background-1920x1080.png` | Image d'arrière-plan Fire TV (1920 × 1080) |
| `firetv-banner-1280x720.png` | Bannière / image promotionnelle |
| Captures d'écran | 3 à 10 captures en 1920 × 1080, prises sur la Fire TV |

## Étapes dans la console (developer.amazon.com)
1. *Apps & Services → Add New App → Android* : titre, catégorie.
2. *In-App Items* :
   - *Add Single IAP → Entitlement* : SKU `com.alftv.pro.unlock`, prix 19,99 €, puis *Submit IAP* ;
   - *Add Subscription* : SKU `com.alftv.pro.subscription`, puis ajoutez une durée **Annuelle**
     avec le SKU `com.alftv.pro.yearly` au prix de 4,99 €, puis *Submit IAP*.
3. *Upload your app file* : téléversez `AlfTVPro-amazon.apk` (release GitHub « latest »).
   Téléchargez la **clé publique** (`AppstoreAuthenticationKey.pem`) proposée par la console et
   déposez-la dans `app/src/amazon/assets/` du dépôt, puis recompilez et téléversez le nouvel APK.
4. *Appstore Details* : descriptions ci-dessus, visuels, lien de la politique de confidentialité.
5. *Content Rating* : remplissez le questionnaire (aucun contenu fourni par l'application).
6. *Availability & Pricing* : gratuit, pays de diffusion.
7. *Submit App* : la validation par Amazon prend généralement quelques jours.
