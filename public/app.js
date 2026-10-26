// Lexia TV — application pour Fire TV (navigateur Silk) pilotée à la télécommande.
// Vues : accueil, salle de préparation, prompteur, gestion locative, écran de diffusion.

(() => {
  const { escapeHtml: esc, formatDate, relativeDay, parseDate, daysFromToday, loadJson, pieceLabel } = Lexia;

  const $app = document.getElementById('app');
  const $overlay = document.getElementById('overlay');
  const $toast = document.getElementById('toast');
  const $live = document.getElementById('live');
  const data = { dossier: null, plaidoirie: null, locatif: null };

  // ---------- Touches ----------
  // Fire TV : la croix directionnelle envoie les flèches, OK = Enter, et les touches média
  // arrivent en MediaPlayPause / MediaRewind / MediaFastForward (ou leurs keyCodes Android).
  const KEYS = {
    ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down',
    Enter: 'ok', Escape: 'back', Backspace: 'back', GoBack: 'back', BrowserBack: 'back',
    ' ': 'play', MediaPlayPause: 'play', MediaPlay: 'play', MediaPause: 'play',
    MediaRewind: 'rew', MediaFastForward: 'ff', MediaTrackPrevious: 'rew', MediaTrackNext: 'ff',
  };
  const KEYCODES = {
    37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 23: 'ok', 27: 'back', 8: 'back', 4: 'back',
    179: 'play', 85: 'play', 227: 'rew', 228: 'ff', 89: 'rew', 90: 'ff',
  };

  function toast(message) {
    $toast.textContent = message;
    $toast.classList.add('visible');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => $toast.classList.remove('visible'), 1800);
  }

  // ---------- Rendu partagé des pièces / bombes ----------
  function findPiece(id) {
    return data.dossier?.pieces.find((p) => p.id === id);
  }

  function pieceRef(id) {
    const p = findPiece(id);
    return p ? `<span class="chip chip-${p.camp}">${esc(pieceLabel(p))} — ${esc(p.titre)}</span>` : `<span class="chip">${esc(id)}</span>`;
  }

  function renderChrono(item) {
    return `
      <div class="card card-chrono">
        <div class="eyebrow">${esc(formatDate(item.date))}</div>
        <h2>${esc(item.titre)}</h2>
        <p class="lead">${esc(item.description)}</p>
        <div class="chips">${(item.pieces || []).map(pieceRef).join('')}</div>
      </div>`;
  }

  function renderPiece(p) {
    const image = p.image ? `<img class="piece-image" src="/data/${esc(p.image)}" alt="">` : '';
    return `
      <div class="card card-piece camp-${esc(p.camp)}">
        <div class="eyebrow"><span class="badge badge-${esc(p.camp)}">${esc(pieceLabel(p))}</span> ${esc(formatDate(p.date))}</div>
        <h2>${esc(p.titre)}</h2>
        ${image}
        ${p.extrait ? `<blockquote>« ${esc(p.extrait)} »</blockquote>` : ''}
        ${p.note ? `<p class="note">${esc(p.note)}</p>` : ''}
      </div>`;
  }

  function renderBombe(b) {
    const side = (label, cls, ref) => {
      const p = findPiece(ref.piece);
      return `
        <div class="duel-side ${cls}">
          <div class="eyebrow">${label}</div>
          <div class="duel-ref">${p ? esc(pieceLabel(p)) + ' — ' + esc(p.titre) : esc(ref.piece)}</div>
          <blockquote>« ${esc(ref.citation)} »</blockquote>
        </div>`;
    };
    return `
      <div class="card card-bombe">
        <div class="eyebrow">Bombe</div>
        <h2>${esc(b.titre)}</h2>
        <div class="duel">
          ${side("Ce qu'ils écrivent", 'duel-adverse', b.adverse)}
          ${side('Ce que montrent les pièces', 'duel-realite', b.realite)}
        </div>
        ${b.phrase ? `<p class="punchline">${esc(b.phrase)}</p>` : ''}
      </div>`;
  }

  // ---------- Vue : accueil ----------
  const home = {
    focus: 0,
    tiles: [
      { view: 'prep', icon: '⚖', title: 'Salle de préparation', desc: 'Chronologie, pièces clés, bombes' },
      { view: 'prompteur', icon: '🎙', title: 'Prompteur', desc: 'Plaidoirie chronométrée par acte' },
      { view: 'locatif', icon: '🏠', title: 'Gestion locative', desc: 'Meublés, arrivées, départs, ménage' },
      { view: 'attente', icon: '📱', title: 'Écran de diffusion', desc: 'Afficher une pièce depuis le téléphone' },
    ],
    enter(root) {
      const d = data.dossier;
      const audience = d && parseDate(d.audience);
      const countdown = audience ? daysFromToday(audience) : null;
      root.innerHTML = `
        <header class="home-header">
          <div>
            <div class="brand">Lexia <span>TV</span></div>
            ${d ? `<div class="subtitle">${esc(d.titre)} · ${esc(d.juridiction)}</div>` : ''}
          </div>
          ${countdown !== null ? `<div class="countdown"><strong>${countdown >= 0 ? 'J-' + countdown : 'J+' + -countdown}</strong><span>Audience ${esc(formatDate(audience))}</span></div>` : ''}
        </header>
        <div class="tiles">
          ${this.tiles.map((t, i) => `
            <button class="tile" data-i="${i}">
              <span class="tile-icon">${t.icon}</span>
              <span class="tile-title">${esc(t.title)}</span>
              <span class="tile-desc">${esc(t.desc)}</span>
            </button>`).join('')}
        </div>
        <footer class="hint">Télécommande sur le téléphone : <strong>${esc(location.origin)}/remote.html</strong></footer>`;
      root.querySelectorAll('.tile').forEach((el) => {
        el.addEventListener('click', () => go(this.tiles[Number(el.dataset.i)].view));
      });
      this.paint();
    },
    paint() {
      $app.querySelectorAll('.tile').forEach((el, i) => el.classList.toggle('focused', i === this.focus));
    },
    key(k) {
      const moves = { left: -1, right: 1, up: -2, down: 2 };
      if (k in moves) {
        const next = this.focus + moves[k];
        if (next >= 0 && next < this.tiles.length) this.focus = next;
        this.paint();
        return true;
      }
      if (k === 'ok') {
        go(this.tiles[this.focus].view);
        return true;
      }
      return false;
    },
  };

  // ---------- Vue : salle de préparation ----------
  const prep = {
    section: 0,
    index: [0, 0, 0],
    zoom: false,
    sections() {
      const d = data.dossier;
      return [
        { title: 'Chronologie', items: d.chronologie, render: renderChrono },
        { title: 'Pièces clés', items: d.pieces, render: renderPiece },
        { title: 'Bombes', items: d.bombes, render: renderBombe },
      ];
    },
    enter(root) {
      this.root = root;
      if (!data.dossier) {
        root.innerHTML = '<p class="empty">Aucun dossier chargé (data/dossier.json).</p>';
        return;
      }
      this.paint();
    },
    paint() {
      const sections = this.sections();
      const s = sections[this.section];
      const i = Math.min(this.index[this.section], Math.max(s.items.length - 1, 0));
      this.root.classList.toggle('zoom', this.zoom);
      this.root.innerHTML = `
        <nav class="tabs">
          ${sections.map((sec, n) => `<span class="tab ${n === this.section ? 'active' : ''}">${esc(sec.title)} <small>${sec.items.length}</small></span>`).join('')}
        </nav>
        <main class="stage">
          ${s.items.length ? s.render(s.items[i]) : '<p class="empty">Rien dans cette section.</p>'}
        </main>
        <footer class="progress">
          <div class="dots">${s.items.map((_, n) => `<i class="${n === i ? 'on' : ''}"></i>`).join('')}</div>
          <div class="hint">◀ ▶ ${i + 1} / ${s.items.length} · ▲ ▼ section · OK ${this.zoom ? 'réduire' : 'agrandir'} · Retour accueil</div>
        </footer>`;
    },
    key(k) {
      const sections = this.sections();
      const count = sections[this.section].items.length;
      switch (k) {
        case 'left':
        case 'rew':
          this.index[this.section] = Math.max(0, this.index[this.section] - 1);
          break;
        case 'right':
        case 'ff':
          this.index[this.section] = Math.min(count - 1, this.index[this.section] + 1);
          break;
        case 'up':
          this.section = (this.section + sections.length - 1) % sections.length;
          break;
        case 'down':
          this.section = (this.section + 1) % sections.length;
          break;
        case 'ok':
          this.zoom = !this.zoom;
          break;
        case 'back':
          if (!this.zoom) return false;
          this.zoom = false;
          break;
        default:
          return false;
      }
      this.paint();
      return true;
    },
  };

  // ---------- Vue : prompteur ----------
  const SPEEDS = [0.4, 0.6, 0.8, 1, 1.25, 1.5, 2, 2.5, 3]; // en rem par seconde

  function formatClock(seconds) {
    const s = Math.max(0, Math.floor(seconds));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  function renderPromptText(text) {
    return String(text || '').split(/\n{2,}/).map((para) => {
      const html = esc(para)
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\[silence(?:\s+([^\]]+))?\]/gi, (_, d) => `<span class="silence">⏸ silence${d ? ' ' + d : ''}</span>`)
        .replace(/\n/g, '<br>');
      return `<p>${html}</p>`;
    }).join('');
  }

  const prompteur = {
    offset: 0,
    speed: 3,
    playing: false,
    times: [],
    enter(root) {
      const p = data.plaidoirie;
      if (!p || !p.actes?.length) {
        root.innerHTML = '<p class="empty">Aucune plaidoirie chargée (data/plaidoirie.json).</p>';
        return;
      }
      if (this.times.length !== p.actes.length) this.times = p.actes.map(() => 0);
      root.innerHTML = `
        <div class="prompt-hud">
          <div class="hud-acte"><span id="hud-title"></span><strong id="hud-acte-time"></strong></div>
          <div class="hud-meta">
            <span>Total <strong id="hud-total"></strong></span>
            <span>Vitesse <strong id="hud-speed"></strong></span>
            <span id="hud-state"></span>
          </div>
        </div>
        <div class="prompt-viewport">
          <div class="reading-line"></div>
          <div class="prompt-text">
            ${p.actes.map((a) => `<section class="acte"><h2>${esc(a.titre)}</h2>${renderPromptText(a.texte)}</section>`).join('')}
            <section class="acte acte-fin"><h2>Fin</h2></section>
          </div>
        </div>
        <footer class="hint">OK lecture/pause · ▲ ▼ vitesse · ◀ ▶ acte · ⏪ ⏩ ajuster · Retour accueil</footer>`;
      this.$text = root.querySelector('.prompt-text');
      this.$sections = [...root.querySelectorAll('.acte')];
      this.measure();
      this.onResize = () => this.measure();
      window.addEventListener('resize', this.onResize);
      this.last = performance.now();
      this.raf = requestAnimationFrame((t) => this.tick(t));
    },
    leave() {
      cancelAnimationFrame(this.raf);
      window.removeEventListener('resize', this.onResize);
      this.playing = false;
    },
    measure() {
      const first = this.$sections[0].offsetTop;
      this.tops = this.$sections.map((s) => s.offsetTop - first);
      this.max = this.tops[this.tops.length - 1];
      this.rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
      this.apply();
    },
    current() {
      let i = 0;
      while (i + 1 < this.tops.length - 1 && this.tops[i + 1] <= this.offset + 1) i++;
      return i;
    },
    tick(now) {
      const dt = Math.min((now - this.last) / 1000, 0.25);
      this.last = now;
      if (this.playing) {
        this.times[this.current()] += dt;
        this.offset += SPEEDS[this.speed] * this.rem * dt;
        if (this.offset >= this.max) {
          this.offset = this.max;
          this.playing = false;
        }
        this.apply();
      }
      this.raf = requestAnimationFrame((t) => this.tick(t));
    },
    apply() {
      this.offset = Math.max(0, Math.min(this.offset, this.max));
      this.$text.style.transform = `translateY(${-this.offset}px)`;
      const actes = data.plaidoirie.actes;
      const i = this.current();
      const target = (actes[i].minutes || 0) * 60;
      const spent = this.times[i];
      const total = this.times.reduce((a, b) => a + b, 0);
      const totalTarget = actes.reduce((a, b) => a + (b.minutes || 0) * 60, 0);
      const $acteTime = document.getElementById('hud-acte-time');
      document.getElementById('hud-title').textContent = actes[i].titre;
      $acteTime.textContent = target ? `${formatClock(spent)} / ${formatClock(target)}` : formatClock(spent);
      $acteTime.classList.toggle('over', target > 0 && spent > target);
      document.getElementById('hud-total').textContent = `${formatClock(total)} / ${formatClock(totalTarget)}`;
      document.getElementById('hud-speed').textContent = `${this.speed + 1}/${SPEEDS.length}`;
      const ended = this.offset >= this.max;
      document.getElementById('hud-state').textContent = this.playing ? '▶ lecture' : ended ? '■ terminé' : '⏸ pause';
      this.$sections.forEach((s, n) => s.classList.toggle('current', n === i));
    },
    jump(direction) {
      const i = this.current();
      if (direction < 0) {
        // Premier appui : retour au début de l'acte ; second appui : acte précédent.
        this.offset = this.offset - this.tops[i] > 2 * this.rem ? this.tops[i] : this.tops[Math.max(0, i - 1)];
      } else {
        this.offset = this.tops[Math.min(this.tops.length - 1, i + 1)];
      }
    },
    key(k) {
      switch (k) {
        case 'ok':
        case 'play':
          if (this.offset >= this.max) this.offset = 0;
          this.playing = !this.playing;
          break;
        case 'up':
          this.speed = Math.min(SPEEDS.length - 1, this.speed + 1);
          break;
        case 'down':
          this.speed = Math.max(0, this.speed - 1);
          break;
        case 'left':
          this.jump(-1);
          break;
        case 'right':
          this.jump(1);
          break;
        case 'rew':
          this.offset -= 4 * this.rem;
          break;
        case 'ff':
          this.offset += 4 * this.rem;
          break;
        default:
          return false;
      }
      this.apply();
      return true;
    },
  };

  // ---------- Vue : gestion locative ----------
  function sameDay(a, b) {
    return a && b && a.getTime() === b.getTime();
  }

  function locatifModel(loc) {
    const today = Lexia.today();
    const nom = (id) => loc.meubles.find((m) => m.id === id)?.nom || id;
    const resas = (loc.reservations || []).map((r) => ({ ...r, arr: parseDate(r.arrivee), dep: parseDate(r.depart) }));
    const menages = (loc.menages || []).map((m) => ({ ...m, d: parseDate(m.date) }));

    const events = [];
    for (const r of resas) {
      events.push({ date: r.arr, heure: r.heureArrivee || '', type: 'checkin', meuble: r.meuble, label: `Arrivée — ${r.voyageur}${r.personnes ? ` (${r.personnes} pers.)` : ''}` });
      events.push({ date: r.dep, heure: r.heureDepart || '', type: 'checkout', meuble: r.meuble, label: `Départ — ${r.voyageur}` });
    }
    for (const m of menages) {
      events.push({ date: m.d, heure: m.heure || '', type: 'menage', meuble: m.meuble, label: `Ménage${m.intervenant && m.intervenant !== 'Ménage' ? ` — ${m.intervenant}` : ''}${m.note ? ` · ${m.note}` : ''}` });
    }

    // Alerte : une arrivée sans ménage depuis le départ précédent dans le même meublé.
    const alerts = [];
    for (const r of resas) {
      const prev = resas.filter((o) => o !== r && o.meuble === r.meuble && o.dep <= r.arr).sort((a, b) => b.dep - a.dep)[0];
      if (!prev || daysFromToday(r.arr) < 0) continue;
      const cleaned = menages.some((m) => m.meuble === r.meuble && m.d >= prev.dep && m.d <= r.arr);
      if (!cleaned) alerts.push(`${nom(r.meuble)} : aucun ménage prévu entre le départ de ${prev.voyageur} et l'arrivée de ${r.voyageur} (${relativeDay(r.arr).toLowerCase()}).`);
    }

    const cards = loc.meubles.map((m) => {
      const own = resas.filter((r) => r.meuble === m.id);
      const stay = own.find((r) => r.arr < today && r.dep > today);
      const arrival = own.find((r) => sameDay(r.arr, today));
      const departure = own.find((r) => sameDay(r.dep, today));
      const menage = menages.find((x) => x.meuble === m.id && sameDay(x.d, today));
      const next = own.filter((r) => r.arr > today).sort((a, b) => a.arr - b.arr)[0];
      let status = 'libre';
      let label = 'Libre';
      let detail = next ? `Prochaine arrivée : ${relativeDay(next.arr).toLowerCase()}` : 'Aucune réservation à venir';
      if (stay) {
        status = 'occupe';
        label = 'Occupé';
        detail = `${stay.voyageur} · départ ${relativeDay(stay.dep).toLowerCase()}`;
      } else if (arrival) {
        status = 'arrivee';
        label = `Arrivée ${arrival.heureArrivee || "aujourd'hui"}`;
        detail = arrival.voyageur;
      } else if (departure) {
        status = 'depart';
        label = `Départ ${departure.heureDepart || "aujourd'hui"}`;
        detail = departure.voyageur;
      }
      const tags = [];
      if (departure && (stay || arrival)) tags.push(`Départ ${departure.heureDepart || ''} — ${departure.voyageur}`);
      if (menage) tags.push(`Ménage ${menage.heure || "aujourd'hui"}`);
      return { ...m, status, label, detail, tags };
    });

    const horizon = 14;
    const upcoming = events
      .filter((e) => e.date && daysFromToday(e.date) >= 0 && daysFromToday(e.date) < horizon)
      .sort((a, b) => a.date - b.date || a.heure.localeCompare(b.heure));
    const days = [];
    for (const e of upcoming) {
      let day = days[days.length - 1];
      if (!day || !sameDay(day.date, e.date)) days.push((day = { date: e.date, events: [] }));
      day.events.push({ ...e, nom: nom(e.meuble) });
    }
    return { cards, days, alerts };
  }

  const locatif = {
    enter(root) {
      if (!data.locatif) {
        root.innerHTML = '<p class="empty">Aucune donnée locative (data/locatif.json).</p>';
        return;
      }
      const { cards, days, alerts } = locatifModel(data.locatif);
      const icons = { checkin: '🔑', checkout: '🧳', menage: '🧽' };
      root.innerHTML = `
        <header class="view-header"><h1>Gestion locative</h1><span>${esc(formatDate(Lexia.today(), { weekday: 'long', day: 'numeric', month: 'long' }))}</span></header>
        ${alerts.map((a) => `<div class="alert">⚠ ${esc(a)}</div>`).join('')}
        <div class="loc-grid">
          <section class="loc-cards">
            ${cards.map((c) => `
              <article class="loc-card status-${c.status}">
                <div class="loc-head"><h2>${esc(c.nom)}</h2><span class="status">${esc(c.label)}</span></div>
                <div class="loc-addr">${esc(c.adresse || '')}</div>
                <div class="loc-detail">${esc(c.detail)}</div>
                ${c.tags.map((t) => `<div class="loc-tag">${esc(t)}</div>`).join('')}
              </article>`).join('')}
          </section>
          <section class="agenda">
            <h2>14 prochains jours</h2>
            <div class="agenda-scroll">
              ${days.length ? days.map((d) => `
                <div class="agenda-day">
                  <h3>${esc(relativeDay(d.date))}</h3>
                  ${d.events.map((e) => `
                    <div class="agenda-event ev-${e.type}">
                      <span class="ev-time">${esc(e.heure)}</span>
                      <span class="ev-icon">${icons[e.type]}</span>
                      <span class="ev-body"><strong>${esc(e.nom)}</strong> ${esc(e.label)}</span>
                    </div>`).join('')}
                </div>`).join('') : '<p class="empty">Rien de prévu.</p>'}
            </div>
          </section>
        </div>
        <footer class="hint">▲ ▼ faire défiler l'agenda · Retour accueil</footer>`;
      this.$scroll = root.querySelector('.agenda-scroll');
    },
    key(k) {
      if (k !== 'up' && k !== 'down') return false;
      const step = this.$scroll.clientHeight * 0.6;
      this.$scroll.scrollBy({ top: k === 'down' ? step : -step, behavior: 'smooth' });
      return true;
    },
  };

  // ---------- Vue : écran de diffusion (attente) ----------
  const attente = {
    enter(root) {
      root.innerHTML = `
        <div class="idle">
          <div class="idle-clock" id="idle-clock"></div>
          <p class="idle-msg">En attente d'une pièce envoyée depuis le téléphone…</p>
          <p class="hint">Ouvrez <strong>${esc(location.origin)}/remote.html</strong> sur le téléphone</p>
        </div>`;
      const tick = () => {
        const el = document.getElementById('idle-clock');
        if (el) el.textContent = new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
      };
      tick();
      this.timer = setInterval(tick, 10000);
    },
    leave() {
      clearInterval(this.timer);
    },
    key() {
      return false;
    },
  };

  // ---------- Navigation ----------
  const VIEWS = { home, prep, prompteur, locatif, attente };
  let current = null;

  function show(name) {
    if (!VIEWS[name]) name = 'home';
    if (current && current.leave) current.leave();
    current = VIEWS[name];
    current.name = name;
    $app.className = `view view-${name}`;
    $app.innerHTML = '';
    current.enter($app);
  }

  function go(name) {
    history.pushState({ view: name }, '', `#${name}`);
    show(name);
  }

  function back() {
    if (current && current.name !== 'home') history.back();
  }

  window.addEventListener('popstate', (e) => {
    const state = e.state || { view: 'home' };
    if (!state.overlay && overlayOpen()) hideOverlay();
    if (!current || state.view !== current.name) show(state.view);
  });

  // ---------- Diffusion depuis le téléphone ----------
  function overlayOpen() {
    return !$overlay.hidden;
  }

  function hideOverlay() {
    $overlay.hidden = true;
    $overlay.innerHTML = '';
  }

  function closeOverlay() {
    if (history.state && history.state.overlay) history.back();
    else hideOverlay();
  }

  function showCast(message) {
    const d = data.dossier;
    let html = '';
    if (message.kind === 'piece') {
      const p = findPiece(message.id);
      if (p) html = renderPiece(p);
    } else if (message.kind === 'bombe') {
      const b = d?.bombes.find((x) => x.id === message.id);
      if (b) html = renderBombe(b);
    } else if (message.kind === 'chrono') {
      const c = d?.chronologie[Number(message.index)];
      if (c) html = renderChrono(c);
    }
    if (!html) return toast('Élément introuvable dans le dossier');
    $overlay.innerHTML = `<div class="cast-banner">📱 Diffusé depuis le téléphone · OK ou Retour pour fermer</div><div class="stage">${html}</div>`;
    if (!overlayOpen()) {
      $overlay.hidden = false;
      history.pushState({ view: current.name, overlay: true }, '', location.hash);
    }
  }

  function onCast(message) {
    switch (message.action) {
      case 'show':
        showCast(message);
        break;
      case 'hide':
        if (overlayOpen()) closeOverlay();
        break;
      case 'key':
        handleKey(message.key);
        break;
      case 'view':
        if (!VIEWS[message.view]) break;
        if (overlayOpen()) {
          // Remplace l'entrée d'historique de la superposition au lieu d'empiler.
          hideOverlay();
          history.replaceState({ view: message.view }, '', `#${message.view}`);
          show(message.view);
        } else if (message.view !== current.name) {
          go(message.view);
        }
        break;
      case 'reload':
        location.reload();
        break;
    }
  }

  function connect() {
    if (!window.EventSource) return;
    const es = new EventSource('/api/events');
    es.onopen = () => $live.classList.add('on');
    es.onerror = () => $live.classList.remove('on');
    es.onmessage = (e) => {
      try {
        onCast(JSON.parse(e.data));
      } catch (err) {
        console.warn('Message de diffusion invalide', err);
      }
    };
  }

  // ---------- Clavier / télécommande ----------
  function handleKey(k) {
    if (overlayOpen()) {
      if (k === 'ok' || k === 'back') closeOverlay();
      return true;
    }
    if (current && current.key(k)) return true;
    if (k === 'back') {
      back();
      return true;
    }
    return false;
  }

  document.addEventListener('keydown', (e) => {
    const k = KEYS[e.key] || KEYCODES[e.keyCode];
    if (!k) return;
    if (k === 'back' && current && current.name === 'home' && !overlayOpen()) return; // laisse Silk gérer la sortie
    if (handleKey(k)) e.preventDefault();
  });

  // ---------- Démarrage ----------
  async function boot() {
    const results = await Promise.allSettled(Object.keys(data).map((name) => loadJson(name)));
    Object.keys(data).forEach((name, i) => {
      if (results[i].status === 'fulfilled') data[name] = results[i].value;
      else console.warn(results[i].reason);
    });
    const initial = location.hash.slice(1);
    history.replaceState({ view: 'home' }, '', '#home');
    show('home');
    if (initial && initial !== 'home' && VIEWS[initial]) go(initial);
    connect();
  }

  boot();
})();
