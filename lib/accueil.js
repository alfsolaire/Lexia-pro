// Écran d'accueil des voyageurs : une adresse secrète par logement
// (/accueil/<clé>/) qui affiche le prénom du voyageur en cours, la météo,
// l'agenda (iCal Google), les actus (RSS) et un diaporama de photos.
// Seul le prénom du voyageur quitte le serveur : jamais son nom, ni les autres séjours.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { eventsBetween } = require('./ical');
const { parseFeed } = require('./rss');

const DAY = 24 * 60 * 60 * 1000;
const IMAGE = /\.(jpe?g|png|webp)$/i;
const CIVILITES = new Set(['m', 'm.', 'mr', 'mr.', 'mme', 'mme.', 'mlle', 'monsieur', 'madame', 'famille', 'mrs', 'mrs.', 'ms', 'ms.', 'family']);

// `exemple` : accepte data/accueil.exemple.json quand il n'y a pas de vraie configuration
// (jamais en mode accueil sur Internet : la clé d'exemple est publique).
function createAccueil(root, { exemple = true } = {}) {
  const cache = new Map();

  function loadConfig() {
    const files = ['data/prive/accueil.json'];
    if (exemple) files.push('data/accueil.exemple.json');
    for (const file of files) {
      const full = path.join(root, file);
      if (fs.existsSync(full)) return JSON.parse(fs.readFileSync(full, 'utf8'));
    }
    return null;
  }

  // Réglages du logement : ses propres clés l'emportent sur les réglages communs.
  function findLogement(cle) {
    const config = loadConfig();
    if (!config || !config.logements || typeof cle !== 'string' || cle.length < 12) return null;
    const wanted = Buffer.from(cle);
    for (const [id, logement] of Object.entries(config.logements)) {
      const key = Buffer.from(String(logement.cle || ''));
      if (key.length === wanted.length && crypto.timingSafeEqual(key, wanted)) {
        const { logements, ...commun } = config;
        return { id, ...commun, ...logement };
      }
    }
    return null;
  }

  // Pour l'appairage des télés : la liste des logements (sans leurs clés) et la clé d'un logement.
  function listLogements() {
    const config = loadConfig();
    return Object.entries(config?.logements || {})
      .filter(([, l]) => l.cle)
      .map(([id, l]) => ({ id, nom: l.nom || id }));
  }

  function cleDe(id) {
    const config = loadConfig();
    const l = config?.logements && Object.prototype.hasOwnProperty.call(config.logements, id) ? config.logements[id] : null;
    return l && l.cle ? String(l.cle) : null;
  }

  // Mot de passe de la page /tv (validation des codes) : variable LEXIA_ADMIN ou "admin.motDePasse".
  function motDePasseAdmin() {
    return process.env.LEXIA_ADMIN || loadConfig()?.admin?.motDePasse || '';
  }

  async function cached(key, ttlMs, load) {
    const hit = cache.get(key);
    if (hit && Date.now() - hit.at < ttlMs) return hit.value;
    try {
      const value = await load();
      cache.set(key, { at: Date.now(), value });
      return value;
    } catch (e) {
      if (hit) return hit.value; // mieux vaut une donnée un peu ancienne qu'un trou à l'écran
      throw e;
    }
  }

  async function fetchText(url, headers = {}) {
    const res = await fetch(url, { headers, signal: AbortSignal.timeout(10000) });
    if (!res.ok) throw new Error(`${url} : ${res.status}`);
    return res.text();
  }

  function resolvePath(p) {
    return path.isAbsolute(p) ? p : path.join(root, p);
  }

  // ---------- Séjours ----------
  function today() {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  }

  // "AAAA-MM-JJ" ou décalage relatif à aujourd'hui ("+2", "-1", "0").
  function parseDay(value) {
    const s = String(value ?? '').trim();
    if (/^[+-]?\d{1,3}$/.test(s)) return new Date(today().getTime() + Number(s) * DAY);
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
    return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
  }

  function atTime(day, hhmm, fallback) {
    const [h, m] = String(hhmm || fallback).split(/[:h]/).map(Number);
    const d = new Date(day);
    d.setHours(h || 0, m || 0, 0, 0);
    return d;
  }

  function prenomDe(r) {
    if (r.prenom) return String(r.prenom).trim();
    const nom = String(r.voyageur || '').trim();
    const [first] = nom.split(/\s+/);
    if (!first) return '';
    if (CIVILITES.has(first.toLowerCase())) return nom;
    return first.toLowerCase().replace(/(^|[-'’])(\p{L})/gu, (_, sep, c) => sep + c.toUpperCase());
  }

  async function loadSejours(source) {
    if (!source) return [];
    let body;
    if (source.url) {
      body = JSON.parse(await cached(`sejours:${source.url}`, 5 * 60 * 1000, () => fetchText(source.url, source.entetes)));
    } else {
      body = JSON.parse(fs.readFileSync(resolvePath(source.fichier || 'data/locatif.json'), 'utf8'));
    }
    return Array.isArray(body) ? body : body.reservations || body.sejours || [];
  }

  // Voyageur à accueillir maintenant : celui qui part aujourd'hui tant que son
  // heure de départ (+1 h) n'est pas passée, sinon celui qui arrive ou séjourne.
  function sejourCourant(sejours, logementId, now = new Date()) {
    const own = sejours
      .filter((r) => String(r.meuble ?? r.logement) === logementId)
      .map((r) => ({ r, arr: parseDay(r.arrivee), dep: parseDay(r.depart) }))
      .filter((x) => x.arr && x.dep);
    const day = today();
    const leaving = own.find((x) => x.dep.getTime() === day.getTime() && now < new Date(atTime(x.dep, x.r.heureDepart, '11:00').getTime() + 60 * 60 * 1000));
    const staying = own.find((x) => x.arr <= day && x.dep > day);
    const chosen = leaving || staying;
    if (!chosen) return null;
    const { r, arr, dep } = chosen;
    return {
      prenom: prenomDe(r),
      langue: r.langue || 'fr',
      arrivee: arr.toISOString(),
      depart: dep.toISOString(),
      heureArrivee: r.heureArrivee || '',
      heureDepart: r.heureDepart || '',
      etape: chosen === leaving ? 'depart' : 'sejour',
    };
  }

  // ---------- Sources extérieures ----------
  async function meteo(s) {
    if (s.latitude === undefined || s.longitude === undefined) return null;
    const base = s.meteoApi || 'https://api.open-meteo.com/v1/forecast';
    const url = `${base}?latitude=${s.latitude}&longitude=${s.longitude}`
      + '&current=temperature_2m,weather_code,wind_speed_10m'
      + '&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5';
    const d = JSON.parse(await cached(url, 15 * 60 * 1000, () => fetchText(url)));
    return {
      maintenant: { temperature: d.current?.temperature_2m, code: d.current?.weather_code, vent: d.current?.wind_speed_10m },
      jours: (d.daily?.time || []).map((date, i) => ({
        date,
        code: d.daily.weather_code[i],
        max: d.daily.temperature_2m_max[i],
        min: d.daily.temperature_2m_min[i],
      })),
    };
  }

  async function agenda(s) {
    const from = today();
    const to = new Date(from.getTime() + 7 * DAY);
    const now = Date.now();
    const lists = await Promise.allSettled((s.agenda || []).map(async (cal) => {
      const text = await cached(`ical:${cal.ical}`, 10 * 60 * 1000, () => fetchText(cal.ical));
      return eventsBetween(text, from, to).map((e) => ({ ...e, agenda: cal.nom || '' }));
    }));
    return lists
      .flatMap((l) => (l.status === 'fulfilled' ? l.value : []))
      .filter((e) => e.journee || new Date(e.fin).getTime() > now)
      .sort((a, b) => a.debut.localeCompare(b.debut))
      .slice(0, 12);
  }

  async function actus(s) {
    const lists = await Promise.allSettled((s.actus || []).map(async (flux) => {
      const xml = await cached(`rss:${flux.url}`, 20 * 60 * 1000, () => fetchText(flux.url));
      return parseFeed(xml, 8).map((it) => ({ titre: it.titre, source: flux.nom || '' }));
    }));
    const ok = lists.filter((l) => l.status === 'fulfilled').map((l) => l.value);
    // Une actu de chaque source à tour de rôle.
    const out = [];
    for (let i = 0; out.length < 20 && ok.some((l) => l[i]); i++) for (const l of ok) if (l[i]) out.push(l[i]);
    return out;
  }

  function photos(s) {
    if (!s.photos?.dossier) return [];
    try {
      return fs.readdirSync(resolvePath(s.photos.dossier)).filter((f) => IMAGE.test(f) && !f.startsWith('.')).sort();
    } catch {
      return [];
    }
  }

  function photoPath(s, name) {
    if (!s.photos?.dossier || name !== path.basename(name) || !IMAGE.test(name)) return null;
    const file = path.join(resolvePath(s.photos.dossier), name);
    return fs.existsSync(file) ? file : null;
  }

  // Fiche du logement fournie par l'application de gestion (Courte durée) : voir
  // docs/contrat-courte-duree.md. Ses valeurs l'emportent sur la configuration locale.
  async function fiche(source) {
    const text = await cached(`fiche:${source.url}`, 2 * 60 * 1000, () => fetchText(source.url, source.entetes));
    return JSON.parse(text);
  }

  // Une mesure de plus de 3 h n'est plus montrée : mieux vaut rien qu'une température fausse.
  function confortRecent(c) {
    if (!c) return null;
    const at = c.majLe ? Date.parse(c.majLe) : NaN;
    if (Number.isNaN(at) || Date.now() - at <= 3 * 60 * 60 * 1000) return c;
    const { temperature, humidite, ...rest } = c;
    return rest;
  }

  async function payload(config) {
    const settle = async (fn) => {
      try {
        return await fn();
      } catch (e) {
        console.warn(`[accueil ${s.id}] ${e.message}`);
        return null;
      }
    };
    let s = config;
    let voyageurFiche;
    if (config.source && config.source.url) {
      const f = await settle(() => fiche(config.source));
      if (f) {
        s = {
          ...config,
          ...(f.logement || {}),
          ...Object.fromEntries(['wifi', 'contact', 'consignes', 'guide', 'confort'].filter((k) => f[k] !== undefined).map((k) => [k, f[k]])),
          id: config.id,
        };
        // `sejours` (recommandé) : les séjours d'hier à demain, Lexia choisit qui accueillir à l'heure qu'il est.
        // `voyageur` : un seul voyageur déjà choisi par l'application.
        if (Array.isArray(f.sejours)) voyageurFiche = f.sejours.map((x) => ({ ...x, meuble: config.id }));
        else if ('voyageur' in f) voyageurFiche = f.voyageur ? [{ ...f.voyageur, meuble: config.id }] : [];
      }
    }
    const [sejours, met, ag, ac] = await Promise.all([
      voyageurFiche || settle(() => loadSejours(s.sejours)),
      settle(() => meteo(s)),
      settle(() => agenda(s)),
      settle(() => actus(s)),
    ]);
    return {
      logement: { nom: s.nom || s.id, ville: s.ville || '' },
      voyageur: sejourCourant(sejours || [], s.id),
      infos: { wifi: s.wifi || null, contact: s.contact || '', consignes: s.consignes || [] },
      guide: Array.isArray(s.guide) ? s.guide : [],
      confort: confortRecent(s.confort),
      // Quiz proposé dans le guide (public/quiz-<nom>.json) ; par défaut celui de la ville s'il existe.
      quiz: s.quiz !== undefined ? s.quiz : /lisieux/i.test(s.ville || '') ? 'lisieux' : null,
      meteo: met,
      agenda: ag || [],
      actus: ac || [],
      photos: photos(s),
      intervalle: Number(s.photos?.intervalle) || 12,
    };
  }

  return { findLogement, payload, photoPath, sejourCourant, prenomDe, listLogements, cleDe, motDePasseAdmin };
}

module.exports = { createAccueil };
