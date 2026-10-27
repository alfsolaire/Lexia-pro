# Consignes pour l'application Courte durée : la « fiche TV » d'un logement

*À transmettre à l'instance qui développe Courte durée / Loyer Malin.*

## Le besoin

Chaque logement aura une Fire TV qui affiche un **écran d'accueil** : « Bienvenue, Roselyne », la météo, l'agenda de la ville, les actus, un diaporama de photos, la **température du logement** et le **guide d'accueil** (le même que celui de l'application Courte durée).

Cet écran est servi par **Lexia TV**, un petit serveur Node (dépôt `alfsolaire/Lexia-pro`), installé sur le même serveur que Courte durée. Il n'y a **aucun ordinateur dans le logement** : la Fire TV ouvre `https://<serveur>/accueil/<clé-secrète>/`.

Lexia TV a besoin d'une seule chose de la part de Courte durée : **une adresse qui renvoie, pour un logement, sa « fiche TV » en JSON**. Lexia l'interroge toutes les 2 minutes au plus et garde la dernière réponse en cas de panne.

## L'adresse à créer

```
GET https://<courte-durée>/<espace>/api/tv/<id-logement>
Authorization: Bearer <jeton>
```

- **Un jeton par logement** (ou par espace), créé et révocable dans les Réglages de Courte durée (par exemple « Écran TV du logement » → « Créer un jeton »). Comparaison à temps constant ; 401 si le jeton est absent ou faux.
- HTTPS seulement. `Cache-Control: no-store`. Réponse en moins de 2 s.
- Limite de débit raisonnable (par exemple 60 requêtes par heure et par jeton).
- Si Lexia tourne sur la même machine, l'adresse peut être locale (`http://127.0.0.1:<port>/…`). Le jeton reste obligatoire.

## Le JSON attendu

Tous les champs sont facultatifs : un champ absent est simplement pris dans la configuration de Lexia, ou pas affiché.

```json
{
  "logement": {
    "nom": "Lisieux Appt N°1",
    "ville": "Lisieux",
    "latitude": 49.146,
    "longitude": 0.226
  },

  "sejours": [
    { "prenom": "Paul",     "langue": "fr", "arrivee": "2026-10-24", "depart": "2026-10-27", "heureArrivee": "16:00", "heureDepart": "11:00" },
    { "prenom": "Roselyne", "langue": "fr", "arrivee": "2026-10-27", "depart": "2026-10-30", "heureArrivee": "13:00", "heureDepart": "11:00" }
  ],

  "confort": {
    "temperature": 20.6,
    "consigne": 21,
    "humidite": 52,
    "eauChaude": true,
    "chauffage": "Manuel",
    "majLe": "2026-10-27T13:13:00+01:00"
  },

  "wifi": { "reseau": "Lisieux-Appt1", "motDePasse": "…" },
  "contact": "Frédéric · 06 …",
  "consignes": ["Départ avant 11 h, clés dans la boîte", "Calme après 22 h"],

  "guide": [
    { "titre": "Arrivée et départ", "icone": "🔑", "texte": "Les clés sont dans la boîte à code…\n\nLe jour du départ…" },
    { "titre": "Bonnes adresses", "icone": "🍽️", "liste": [ { "nom": "Boulangerie du Centre", "detail": "Rue Henry Chéron · 7 h – 19 h" } ] }
  ]
}
```

### `sejours` : qui accueillir

- Envoyer **les séjours qui touchent hier, aujourd'hui et demain** pour ce logement, sans les séjours annulés. Lexia choisit lui-même qui afficher à l'heure qu'il est :
  - le jour du départ, le voyageur sortant (« Bon retour, Paul ») jusqu'à son `heureDepart` + 1 h ;
  - ensuite, ou pendant le séjour, le voyageur arrivé ou attendu ce jour-là (« Bienvenue, Roselyne ») ;
  - sinon personne : « Bienvenue » seul.
- Dates `AAAA-MM-JJ` et heures `HH:MM`, à l'heure de France.
- **Le prénom seulement** (`prenom`), avec la majuscule voulue. Jamais le nom de famille, ni l'e-mail, ni le téléphone, ni le montant, ni la plateforme : l'écran est visible de tous dans le logement. À défaut de `prenom`, Lexia prend le premier mot de `voyageur` (« roselyne CHOMAUD » → « Roselyne »), mais il vaut mieux l'envoyer.
- `langue` : `fr` ou `en` (l'écran et le guide intégré passent en anglais). Une autre langue est affichée en français pour l'instant.
- Autre forme possible : `"voyageur": { …un seul séjour… }` ou `"voyageur": null`, si Courte durée préfère choisir elle-même.

### `confort` : la température du logement

- Les données que Courte durée connaît déjà par ses actions automatiques (l'e-mail d'arrivée dit « chauffage Manuel consigne 21 °C ✓, ballon allumé ✓ »).
- `temperature` : la température **mesurée** dans le logement, en °C, si une sonde ou un thermostat la remonte. `consigne` : la consigne en cours. `humidite` en %. `eauChaude` : `true` si le ballon est allumé et l'eau chaude prête.
- `majLe` (ISO 8601) : l'heure de la mesure. **Au-delà de 3 h**, Lexia n'affiche plus la température ni l'humidité.
- L'écran est en **lecture seule** : rien ne se règle depuis la télé.

### `guide` : le guide d'accueil

- **Le même contenu que le guide d'accueil de l'application Courte durée**, dans la langue du voyageur en cours si Courte durée l'a en plusieurs langues.
- Chaque rubrique : `titre`, `icone` (un emoji, facultatif), `texte` (paragraphes séparés par une ligne vide, sans HTML) et/ou `liste` d'éléments `{ nom, detail }`.
- Lexia ajoute lui-même deux rubriques à la fin : **Urgences** (112, 15, 17, 18, 114, pharmacie de garde 3237) et **Quiz** (pour Lisieux).
- Écrire pour une télé lue à 3 m : des rubriques courtes, une idée par paragraphe.

### `wifi`, `contact`, `consignes`

Affichés en permanence dans le cadre « Infos pratiques ». Ne mettre dans `contact` que ce que le voyageur doit voir.

## Ce que Lexia fait de son côté (déjà prêt)

Dans `data/prive/accueil.json` sur le serveur (fichier jamais publié) :

```json
{
  "logements": {
    "lisieux1": {
      "cle": "<clé de l'adresse de la TV, 20 caractères ou plus>",
      "source": {
        "url": "https://<courte-durée>/<espace>/api/tv/lisieux1",
        "entetes": { "Authorization": "Bearer <jeton créé dans Courte durée>" }
      }
    }
  }
}
```

## Pour vérifier

1. `curl -H "Authorization: Bearer <jeton>" https://<courte-durée>/<espace>/api/tv/<id>` renvoie le JSON ci-dessus.
2. Sans jeton ou avec un mauvais jeton : 401, et aucune donnée.
3. Un logement sans séjour aujourd'hui : `"sejours": []`.
4. Aucun nom de famille dans la réponse (un test automatique le vérifie, idéalement).
5. Ouvrir `https://<serveur>/accueil/<clé>/` dans un navigateur : le prénom, la température et le guide apparaissent en moins de 2 minutes.

## Plus tard (si utile)

- Dans les Réglages de Courte durée, montrer l'adresse de l'écran TV de chaque logement (avec un code QR), pour l'installer sur la Fire TV.
- Un message ponctuel du loueur à afficher sur la télé (« Le ménage passe demain à 10 h ») : un champ `message` dans la fiche.
