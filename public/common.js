// Utilitaires partagés entre l'app TV et la télécommande mobile.

const Lexia = (() => {
  const DAY = 24 * 60 * 60 * 1000;

  function today() {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  }

  // "2024-03-12" ou décalage relatif "+2" / "-1" / "0" par rapport à aujourd'hui.
  function parseDate(value) {
    if (value === undefined || value === null || value === '') return null;
    const s = String(value).trim();
    if (/^[+-]?\d{1,4}$/.test(s) && !/^\d{4}$/.test(s)) {
      return new Date(today().getTime() + Number(s) * DAY);
    }
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y, (m || 1) - 1, d || 1);
  }

  function daysFromToday(date) {
    return Math.round((date - today()) / DAY);
  }

  function formatDate(value, options = { day: 'numeric', month: 'long', year: 'numeric' }) {
    const d = value instanceof Date ? value : parseDate(value);
    return d ? d.toLocaleDateString('fr-FR', options) : '';
  }

  function relativeDay(date) {
    const n = daysFromToday(date);
    if (n === 0) return "Aujourd'hui";
    if (n === 1) return 'Demain';
    if (n === -1) return 'Hier';
    return formatDate(date, { weekday: 'long', day: 'numeric', month: 'long' });
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  }

  async function loadJson(name) {
    const res = await fetch(`/data/${name}.json`, { cache: 'no-cache' });
    if (!res.ok) throw new Error(`Impossible de charger ${name}.json (${res.status})`);
    return res.json();
  }

  function pieceLabel(piece) {
    if (!piece) return '';
    const camp = piece.camp === 'adverse' ? 'adverse' : 'demandeur';
    return `Pièce ${camp} n°${piece.numero}`;
  }

  return { DAY, today, parseDate, daysFromToday, formatDate, relativeDay, escapeHtml, loadJson, pieceLabel };
})();
