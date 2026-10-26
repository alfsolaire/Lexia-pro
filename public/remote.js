// Télécommande mobile : choisir une pièce / bombe et l'afficher sur la TV,
// ou piloter l'app TV comme avec la télécommande Fire TV.

(() => {
  const { escapeHtml: esc, formatDate, loadJson, pieceLabel } = Lexia;

  const $list = document.getElementById('list');
  const $pad = document.getElementById('pad');
  const $search = document.getElementById('search');
  const $status = document.getElementById('status');
  const $toast = document.getElementById('toast');
  let dossier = null;
  let tab = 'pieces';

  function toast(message) {
    $toast.textContent = message;
    $toast.classList.add('visible');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => $toast.classList.remove('visible'), 1600);
  }

  async function send(message, confirmation) {
    try {
      const res = await fetch('/api/cast', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(message),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error || res.status);
      if (body.ecrans === 0) toast('Aucune TV connectée');
      else if (confirmation) toast(confirmation);
      if (navigator.vibrate) navigator.vibrate(15);
    } catch (e) {
      toast(`Échec : ${e.message}`);
    }
  }

  function items() {
    if (!dossier) return [];
    if (tab === 'pieces') {
      return dossier.pieces.map((p) => ({
        kind: 'piece', id: p.id, camp: p.camp,
        title: p.titre, meta: `${pieceLabel(p)} · ${formatDate(p.date)}`, text: p.extrait || '',
      }));
    }
    if (tab === 'bombes') {
      return dossier.bombes.map((b) => ({
        kind: 'bombe', id: b.id, camp: 'bombe',
        title: b.titre, meta: 'Bombe', text: b.phrase || '',
      }));
    }
    return dossier.chronologie.map((c, index) => ({
      kind: 'chrono', index, camp: 'chrono',
      title: c.titre, meta: formatDate(c.date), text: c.description || '',
    }));
  }

  function renderList() {
    const q = $search.value.trim().toLowerCase();
    const shown = items().filter((it) => !q || `${it.title} ${it.meta} ${it.text}`.toLowerCase().includes(q));
    $list.innerHTML = shown.length
      ? shown.map((it, n) => `
        <button class="item item-${esc(it.camp)}" data-n="${n}">
          <span class="item-meta">${esc(it.meta)}</span>
          <span class="item-title">${esc(it.title)}</span>
          ${it.text ? `<span class="item-text">${esc(it.text)}</span>` : ''}
          <span class="item-cast">Afficher sur la TV ▸</span>
        </button>`).join('')
      : '<p class="muted center">Aucun résultat.</p>';
    $list.querySelectorAll('.item').forEach((el) => {
      el.addEventListener('click', () => {
        const it = shown[Number(el.dataset.n)];
        send({ action: 'show', kind: it.kind, id: it.id, index: it.index }, `« ${it.title} » affiché`);
      });
    });
  }

  function selectTab(name) {
    tab = name;
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === name));
    const isPad = name === 'pad';
    $pad.hidden = !isPad;
    $list.hidden = isPad;
    $search.hidden = isPad;
    if (!isPad) renderList();
  }

  document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => selectTab(t.dataset.tab)));
  $search.addEventListener('input', renderList);
  document.getElementById('hide').addEventListener('click', () => send({ action: 'hide' }, 'Affichage fermé'));
  $pad.querySelectorAll('[data-key]').forEach((b) => b.addEventListener('click', () => send({ action: 'key', key: b.dataset.key })));
  $pad.querySelectorAll('[data-view]').forEach((b) => b.addEventListener('click', () => send({ action: 'view', view: b.dataset.view })));

  async function refreshStatus() {
    try {
      const info = await (await fetch('/api/info', { cache: 'no-store' })).json();
      $status.textContent = info.ecrans ? `📺 ${info.ecrans} TV` : 'Aucune TV';
      $status.classList.toggle('on', info.ecrans > 0);
    } catch {
      $status.textContent = 'Hors ligne';
      $status.classList.remove('on');
    }
  }

  async function boot() {
    try {
      dossier = await loadJson('dossier');
      document.getElementById('dossier').textContent = dossier.titre;
    } catch (e) {
      $list.innerHTML = `<p class="muted center">${esc(e.message)}</p>`;
    }
    selectTab('pieces');
    refreshStatus();
    setInterval(refreshStatus, 5000);
  }

  boot();
})();
