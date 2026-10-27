// Appairage d'une télé sans rien taper : la télé affiche un code à 6 chiffres, le loueur le
// valide depuis son téléphone (page /tv, protégée par mot de passe) en choisissant le logement,
// et seule cette télé reçoit l'adresse secrète /accueil/<clé>/.
//
// Le code ne vaut que 10 minutes et ne donne rien à lui seul : il faut aussi le mot de passe.
// La télé suit l'état avec un identifiant long et aléatoire, que personne d'autre ne connaît.

const crypto = require('crypto');

const DUREE_MS = 10 * 60 * 1000;
const MAX_EN_ATTENTE = 200;
const MAX_PAR_IP_MINUTE = 6;
const MAX_ECHECS = 10; // validations ratées en 10 minutes avant blocage

function createAppairage() {
  const demandes = new Map(); // id -> { code, expire, ip, url }
  const parIp = new Map(); // ip -> [horodatages]
  let echecs = [];

  function nettoyer(now = Date.now()) {
    for (const [id, d] of demandes) if (d.expire < now) demandes.delete(id);
    for (const [ip, list] of parIp) {
      const recent = list.filter((t) => now - t < 60 * 1000);
      if (recent.length) parIp.set(ip, recent);
      else parIp.delete(ip);
    }
    echecs = echecs.filter((t) => now - t < DUREE_MS);
  }

  function nouveauCode() {
    const enCours = new Set([...demandes.values()].map((d) => d.code));
    for (;;) {
      const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
      if (!enCours.has(code)) return code;
    }
  }

  // Une télé demande un code.
  function creer(ip) {
    const now = Date.now();
    nettoyer(now);
    const recent = parIp.get(ip) || [];
    if (recent.length >= MAX_PAR_IP_MINUTE || demandes.size >= MAX_EN_ATTENTE) return null;
    recent.push(now);
    parIp.set(ip, recent);
    const id = crypto.randomBytes(24).toString('base64url');
    const demande = { code: nouveauCode(), expire: now + DUREE_MS, ip, url: null };
    demandes.set(id, demande);
    return { id, code: demande.code, expireDans: Math.round(DUREE_MS / 1000) };
  }

  // La télé demande où en est son code.
  function etat(id) {
    nettoyer();
    const d = typeof id === 'string' ? demandes.get(id) : null;
    if (!d) return { etat: 'expire' };
    if (!d.url) return { etat: 'attente' };
    demandes.delete(id); // l'adresse n'est remise qu'une fois
    return { etat: 'valide', url: d.url };
  }

  // Le loueur valide un code depuis son téléphone.
  function valider(code, url) {
    nettoyer();
    if (echecs.length >= MAX_ECHECS) return { ok: false, erreur: 'Trop d\'essais : réessayez dans quelques minutes.' };
    const propre = String(code || '').replace(/\D/g, '');
    const trouve = [...demandes.values()].find((d) => d.code === propre && !d.url);
    if (!trouve) {
      echecs.push(Date.now());
      return { ok: false, erreur: 'Code inconnu ou expiré.' };
    }
    trouve.url = url;
    return { ok: true };
  }

  return { creer, etat, valider };
}

module.exports = { createAppairage };
