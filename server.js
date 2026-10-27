// Serveur Lexia TV : sert l'application TV, la télécommande mobile,
// les données JSON, et relaie les commandes téléphone -> TV (Server-Sent Events).
// Sert aussi l'écran d'accueil des voyageurs (/accueil/<clé>/).
// Aucune dépendance : `node server.js` suffit.
//
// LEXIA_MODE=accueil : seul l'écran d'accueil est servi (pour un serveur sur Internet) ;
// dossier, plaidoirie, données locatives et télécommande ne sont plus accessibles.

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { createAccueil } = require('./lib/accueil');

const PORT = Number(process.env.PORT) || 8080;
const HOST = process.env.HOST || '0.0.0.0';
const MODE_ACCUEIL = process.env.LEXIA_MODE === 'accueil';
const ROOT = __dirname;
const PUBLIC = path.join(ROOT, 'public');
const ACCUEIL_FILES = new Set(['', 'index.html', 'accueil.js', 'accueil.css', 'common.js', 'quiz.html', 'quiz.js', 'quiz.css']);
const accueil = createAccueil(ROOT, { exemple: !MODE_ACCUEIL });
const STATIC_DIRS = { '/data/': path.join(ROOT, 'data'), '/': path.join(ROOT, 'public') };

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.pdf': 'application/pdf',
};

const clients = new Set();

function lanAddresses() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal)
    .map((i) => i.address);
}

function broadcast(message) {
  const payload = `data: ${JSON.stringify(message)}\n\n`;
  for (const res of clients) res.write(payload);
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': MIME['.json'], 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

function sendFile(res, file, extraHeaders = {}) {
  fs.readFile(file, (err, content) => {
    if (err) return sendJson(res, 404, { error: 'not found' });
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-cache',
      ...extraHeaders,
    });
    res.end(content);
  });
}

// /accueil/<clé>/…  — la page, ses fichiers, /api et /photo/<nom>.
async function serveAccueil(req, res, pathname) {
  const [, , cle, ...rest] = pathname.split('/');
  const logement = accueil.findLogement(decodeURIComponent(cle || ''));
  if (!logement) return sendJson(res, 404, { error: 'not found' });
  const privacy = { 'X-Robots-Tag': 'noindex, nofollow', 'Referrer-Policy': 'no-referrer' };
  if (rest.length === 0) {
    res.writeHead(301, { Location: `${encodeURIComponent(cle)}/` });
    return res.end();
  }
  const sub = rest.join('/');
  if (sub === 'api') {
    const body = await accueil.payload(logement);
    res.writeHead(200, { 'Content-Type': MIME['.json'], 'Cache-Control': 'no-store', ...privacy });
    return res.end(JSON.stringify(body));
  }
  if (rest[0] === 'photo' && rest.length === 2) {
    const file = accueil.photoPath(logement, decodeURIComponent(rest[1]));
    return file ? sendFile(res, file, { 'Cache-Control': 'max-age=86400', ...privacy }) : sendJson(res, 404, { error: 'not found' });
  }
  if (ACCUEIL_FILES.has(sub) || /^quiz-[a-z0-9-]+\.json$/.test(sub)) {
    return sendFile(res, path.join(PUBLIC, sub === '' || sub === 'index.html' ? 'accueil.html' : sub), privacy);
  }
  return sendJson(res, 404, { error: 'not found' });
}

function serveStatic(req, res, pathname) {
  const prefix = pathname.startsWith('/data/') ? '/data/' : '/';
  const base = STATIC_DIRS[prefix];
  let rel = decodeURIComponent(pathname.slice(prefix.length));
  if (rel === '' || rel.endsWith('/')) rel += 'index.html';
  const file = path.resolve(base, rel);
  if (file !== base && !file.startsWith(base + path.sep)) return sendJson(res, 403, { error: 'forbidden' });
  // Clés des logements, Wi-Fi… : jamais servis tels quels.
  if (prefix === '/data/' && (rel.startsWith('prive/') || rel.startsWith('accueil'))) return sendJson(res, 404, { error: 'not found' });

  sendFile(res, file);
}

function readBody(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > limit) {
        reject(new Error('payload too large'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const { pathname } = new URL(req.url, 'http://localhost');

  if (pathname.startsWith('/accueil/') && req.method === 'GET') {
    try {
      return await serveAccueil(req, res, pathname);
    } catch (e) {
      console.error(e);
      return sendJson(res, 500, { error: 'erreur' });
    }
  }
  if (MODE_ACCUEIL) return sendJson(res, 404, { error: 'not found' });

  if (pathname === '/api/events' && req.method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-store',
      Connection: 'keep-alive',
    });
    res.write('retry: 2000\n\n');
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (pathname === '/api/cast' && req.method === 'POST') {
    try {
      const message = JSON.parse(await readBody(req));
      if (!message || typeof message.action !== 'string') throw new Error('action manquante');
      broadcast(message);
      return sendJson(res, 200, { ok: true, ecrans: clients.size });
    } catch (e) {
      return sendJson(res, 400, { error: e.message });
    }
  }

  if (pathname === '/api/info' && req.method === 'GET') {
    return sendJson(res, 200, { port: PORT, adresses: lanAddresses(), ecrans: clients.size });
  }

  if (req.method === 'GET') return serveStatic(req, res, pathname);
  sendJson(res, 405, { error: 'method not allowed' });
});

// Garde les connexions SSE ouvertes derrière les box / proxys.
setInterval(() => {
  for (const res of clients) res.write(': ping\n\n');
}, 25000).unref();

server.listen(PORT, HOST, () => {
  console.log(`Lexia TV prête sur le port ${PORT}${MODE_ACCUEIL ? ' (mode accueil seul)' : ''}`);
  if (MODE_ACCUEIL) return;
  for (const ip of lanAddresses()) {
    console.log(`  TV (Fire TV / Silk) : http://${ip}:${PORT}/`);
    console.log(`  Téléphone          : http://${ip}:${PORT}/remote.html`);
  }
});
