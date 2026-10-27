// Écran d'accueil des voyageurs, ouvert sur la Fire TV du logement à l'adresse
// https://<serveur>/accueil/<clé>/ . Toutes les données viennent de ./api (même clé).
// Télécommande : OK ou ▼ ouvre le guide d'accueil (▲ ▼ rubrique, OK quiz, ◀ ou Retour fermer),
// ▲ photo seule, ◀ ▶ photo précédente/suivante.

(() => {
  const esc = Lexia.escapeHtml;
  const REFRESH_MS = 5 * 60 * 1000;
  const GUIDE_IDLE_MS = 3 * 60 * 1000;

  const TEXTS = {
    fr: {
      hello: (p) => (p ? `Bienvenue, ${p}` : 'Bienvenue'),
      bye: (p) => (p ? `Bon retour, ${p}` : 'Bon retour'),
      until: (d) => `jusqu'au ${d}`,
      departToday: (h) => `Départ aujourd'hui${h ? ` avant ${h}` : ''} — merci pour votre séjour`,
      departAt: (h) => `départ ${h}`,
      weather: 'Météo',
      today: "Aujourd'hui",
      tomorrow: 'Demain',
      week: 'Cette semaine',
      allDay: 'Journée',
      infos: 'Infos pratiques',
      wifi: 'Wi-Fi',
      password: 'Mot de passe',
      checkout: 'Départ',
      contact: 'Contact',
      wind: 'vent',
      inside: 'Dans le logement',
      setpoint: 'consigne',
      humidity: 'humidité',
      hotWater: 'Eau chaude',
      hotWaterOn: 'prête',
      hotWaterOff: 'en chauffe',
      hint: 'OK · Guide d\'accueil',
      guide: "Guide d'accueil",
      guideHint: '▲ ▼ rubrique · ◀ ou Retour pour fermer',
      emergency: 'Urgences',
      emergencyList: [
        ['112', 'Numéro d\'urgence européen'],
        ['15', 'SAMU — urgence médicale'],
        ['17', 'Police secours'],
        ['18', 'Pompiers'],
        ['114', 'Urgences par SMS (sourds et malentendants)'],
        ['3237', 'Pharmacie de garde'],
      ],
      quiz: 'Quiz',
      quizText: 'Jouez en famille à la télécommande : 10 questions, chacun son tour.',
      quizGo: 'OK pour jouer',
    },
    en: {
      hello: (p) => (p ? `Welcome, ${p}` : 'Welcome'),
      bye: (p) => (p ? `Safe travels, ${p}` : 'Safe travels'),
      until: (d) => `until ${d}`,
      departToday: (h) => `Check-out today${h ? ` before ${h}` : ''} — thank you for staying with us`,
      departAt: (h) => `check-out ${h}`,
      weather: 'Weather',
      today: 'Today',
      tomorrow: 'Tomorrow',
      week: 'This week',
      allDay: 'All day',
      infos: 'Good to know',
      wifi: 'Wi-Fi',
      password: 'Password',
      checkout: 'Check-out',
      contact: 'Contact',
      wind: 'wind',
      inside: 'Inside',
      setpoint: 'set to',
      humidity: 'humidity',
      hotWater: 'Hot water',
      hotWaterOn: 'ready',
      hotWaterOff: 'heating up',
      hint: 'OK · House guide',
      guide: 'House guide',
      guideHint: '▲ ▼ section · ◀ or Back to close',
      emergency: 'Emergency',
      emergencyList: [
        ['112', 'European emergency number'],
        ['15', 'Medical emergency (SAMU)'],
        ['17', 'Police'],
        ['18', 'Fire brigade'],
        ['114', 'Emergency by text (deaf and hard of hearing)'],
        ['3237', 'Pharmacy on duty'],
      ],
      quiz: 'Quiz',
      quizText: 'A remote-control quiz for the whole family (in French).',
      quizGo: 'OK to play',
    },
  };

  // Codes météo WMO (Open-Meteo).
  const WEATHER = [
    [[0], '☀️', 'Ensoleillé', 'Sunny'],
    [[1], '🌤️', 'Plutôt ensoleillé', 'Mostly sunny'],
    [[2], '⛅', 'Éclaircies', 'Partly cloudy'],
    [[3], '☁️', 'Couvert', 'Cloudy'],
    [[45, 48], '🌫️', 'Brouillard', 'Fog'],
    [[51, 53, 55, 56, 57], '🌦️', 'Bruine', 'Drizzle'],
    [[61, 63, 65, 66, 67], '🌧️', 'Pluie', 'Rain'],
    [[71, 73, 75, 77], '❄️', 'Neige', 'Snow'],
    [[80, 81, 82], '🌦️', 'Averses', 'Showers'],
    [[85, 86], '🌨️', 'Averses de neige', 'Snow showers'],
    [[95, 96, 99], '⛈️', 'Orage', 'Thunderstorm'],
  ];

  const state = { data: null, lang: 'fr', photos: [], photoIndex: -1, photoTimer: null, newsIndex: 0, front: 0, guide: null };
  const $ = (id) => document.getElementById(id);

  const t = () => TEXTS[state.lang] || TEXTS.fr;
  const locale = () => (state.lang === 'en' ? 'en-GB' : 'fr-FR');

  function weather(code) {
    const w = WEATHER.find(([codes]) => codes.includes(code));
    return w ? { icon: w[1], label: state.lang === 'en' ? w[3] : w[2] } : { icon: '🌡️', label: '' };
  }

  function dayName(date) {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    const diff = Math.round((d - Lexia.today()) / Lexia.DAY);
    if (diff === 0) return t().today;
    if (diff === 1) return t().tomorrow;
    return d.toLocaleDateString(locale(), { weekday: 'long' });
  }

  function longDate(iso) {
    return new Date(iso).toLocaleDateString(locale(), { weekday: 'long', day: 'numeric', month: 'long' });
  }

  function show(id, html) {
    const el = $(id);
    el.hidden = !html;
    el.innerHTML = html || '';
  }

  // ---------- Rendu ----------
  function renderWelcome() {
    const { voyageur: v, logement } = state.data;
    const place = [logement.nom, logement.ville].filter(Boolean).join(' · ');
    if (v && v.etape === 'depart') {
      $('hello').textContent = t().bye(v.prenom);
      $('stay').textContent = t().departToday(v.heureDepart);
    } else if (v) {
      $('hello').textContent = t().hello(v.prenom);
      const until = t().until(longDate(v.depart)) + (v.heureDepart ? ` (${t().departAt(v.heureDepart)})` : '');
      $('stay').textContent = `${place} — ${until}`;
    } else {
      $('hello').textContent = t().hello('');
      $('stay').textContent = place;
    }
    document.title = $('hello').textContent;
  }

  function renderClock() {
    const now = new Date();
    $('time').textContent = now.toLocaleTimeString(locale(), { hour: '2-digit', minute: '2-digit' });
    $('date').textContent = now.toLocaleDateString(locale(), { weekday: 'long', day: 'numeric', month: 'long' });
  }

  function formatTemp(v) {
    return `${Number(v).toLocaleString(locale(), { maximumFractionDigits: 1 })}°`;
  }

  // Température, consigne et eau chaude du logement, fournies par l'application de gestion.
  function renderConfort() {
    const c = state.data.confort;
    if (!c) return '';
    const parts = [];
    if (c.temperature !== undefined && c.temperature !== null) {
      parts.push(`<span class="inside-temp">${formatTemp(c.temperature)}</span>`);
    }
    const details = [];
    if (c.consigne !== undefined && c.consigne !== null) details.push(`${t().setpoint} ${formatTemp(c.consigne)}`);
    if (c.humidite !== undefined && c.humidite !== null) details.push(`${t().humidity} ${Math.round(c.humidite)} %`);
    if (c.eauChaude !== undefined && c.eauChaude !== null) details.push(`${t().hotWater} ${c.eauChaude ? t().hotWaterOn : t().hotWaterOff}`);
    if (!parts.length && !details.length) return '';
    return `
      <div class="inside">
        <span class="inside-label">🏠 ${esc(t().inside)}</span>
        ${parts.join('')}
        <small>${esc(details.join(' · '))}</small>
      </div>`;
  }

  function renderMeteo() {
    const m = state.data.meteo;
    const confort = renderConfort();
    if (!m || m.maintenant.temperature === undefined) {
      return show('meteo', confort ? `<h2>${esc(t().inside)}</h2>${confort}` : '');
    }
    const now = weather(m.maintenant.code);
    const days = m.jours.slice(1, 5).map((d) => {
      const w = weather(d.code);
      return `<li><span class="d">${esc(dayName(d.date))}</span><span class="i">${w.icon}</span><span class="t">${Math.round(d.max)}°<small>${Math.round(d.min)}°</small></span></li>`;
    }).join('');
    const today = m.jours[0];
    show('meteo', `
      <h2>${esc(t().weather)}${state.data.logement.ville ? ` · ${esc(state.data.logement.ville)}` : ''}</h2>
      <div class="now">
        <span class="now-icon">${now.icon}</span>
        <span class="now-temp">${Math.round(m.maintenant.temperature)}°</span>
        <span class="now-label">${esc(now.label)}${today ? `<br><small>${Math.round(today.min)}° / ${Math.round(today.max)}° · ${esc(t().wind)} ${Math.round(m.maintenant.vent || 0)} km/h</small>` : ''}</span>
      </div>
      <ul class="days">${days}</ul>
      ${confort}`);
  }

  function renderAgenda() {
    const events = state.data.agenda.slice(0, 5);
    if (!events.length) return show('agenda', '');
    show('agenda', `
      <h2>${esc(t().week)}</h2>
      <ul class="events">
        ${events.map((e) => `
          <li>
            <span class="when">${esc(dayName(e.debut))}<small>${e.journee ? esc(t().allDay) : new Date(e.debut).toLocaleTimeString(locale(), { hour: '2-digit', minute: '2-digit' })}</small></span>
            <span class="what">${esc(e.titre)}${e.lieu ? `<small>${esc(e.lieu)}</small>` : ''}</span>
          </li>`).join('')}
      </ul>`);
  }

  function renderInfos() {
    const { infos, voyageur: v } = state.data;
    const rows = [];
    if (infos.wifi) {
      rows.push(`<div class="wifi"><span>${esc(t().wifi)}</span><strong>${esc(infos.wifi.reseau)}</strong></div>`);
      if (infos.wifi.motDePasse) rows.push(`<div class="wifi"><span>${esc(t().password)}</span><strong class="mono">${esc(infos.wifi.motDePasse)}</strong></div>`);
    }
    if (v && v.heureDepart) rows.push(`<div class="row"><span>${esc(t().checkout)}</span><strong>${esc(dayName(v.depart))} · ${esc(v.heureDepart)}</strong></div>`);
    if (infos.contact) rows.push(`<div class="row"><span>${esc(t().contact)}</span><strong>${esc(infos.contact)}</strong></div>`);
    if (infos.consignes.length) rows.push(`<ul class="rules">${infos.consignes.map((c) => `<li>${esc(c)}</li>`).join('')}</ul>`);
    show('infos', rows.length ? `<h2>${esc(t().infos)}</h2>${rows.join('')}` : '');
  }

  function renderNews() {
    const news = state.data.actus;
    $('news').hidden = !news.length;
    if (!news.length) return;
    const n = news[state.newsIndex % news.length];
    const $title = document.querySelector('.news-title');
    $title.classList.remove('in');
    void $title.offsetWidth; // relance l'animation
    document.querySelector('.news-source').textContent = n.source;
    $title.textContent = n.titre;
    $title.classList.add('in');
  }

  // ---------- Guide d'accueil ----------
  function guideSections() {
    const sections = state.data.guide.map((g) => ({ ...g }));
    sections.push({
      titre: t().emergency,
      icone: '🚑',
      liste: t().emergencyList.map(([nom, detail]) => ({ nom, detail })),
    });
    if (state.data.quiz) sections.push({ titre: t().quiz, icone: '🎲', texte: t().quizText, quiz: state.data.quiz });
    return sections;
  }

  function renderGuideContent(section) {
    const paragraphs = String(section.texte || '').split(/\n{2,}/).filter(Boolean)
      .map((p) => `<p>${esc(p).replace(/\n/g, '<br>')}</p>`).join('');
    const list = (section.liste || []).map((it) => `
      <li><strong>${esc(it.nom)}</strong>${it.detail ? `<span>${esc(it.detail)}</span>` : ''}</li>`).join('');
    return `
      <h2>${section.icone ? `<span class="g-icon">${esc(section.icone)}</span>` : ''}${esc(section.titre)}</h2>
      ${paragraphs}
      ${list ? `<ul class="g-list">${list}</ul>` : ''}
      ${section.quiz ? `<p class="g-go">${esc(t().quizGo)}</p>` : ''}`;
  }

  function renderGuide() {
    const sections = guideSections();
    const i = Math.min(state.guide.index, sections.length - 1);
    document.querySelector('.guide-nav').innerHTML = `
      <h1>${esc(t().guide)}</h1>
      <ul>${sections.map((s, n) => `<li class="${n === i ? 'active' : ''}">${s.icone ? `<span class="g-icon">${esc(s.icone)}</span>` : ''}${esc(s.titre)}</li>`).join('')}</ul>
      <p class="g-hint">${esc(t().guideHint)}</p>`;
    const $content = document.querySelector('.guide-content');
    $content.innerHTML = renderGuideContent(sections[i]);
    $content.scrollTop = 0;
  }

  function openGuide() {
    state.guide = { index: 0 };
    $('guide').hidden = false;
    document.body.classList.add('guide-open');
    history.pushState({ guide: true }, '');
    renderGuide();
    touchGuide();
  }

  function hideGuide() {
    state.guide = null;
    clearTimeout(state.guideTimer);
    $('guide').hidden = true;
    document.body.classList.remove('guide-open');
  }

  function closeGuide() {
    if (history.state && history.state.guide) history.back();
    else hideGuide();
  }

  // Le guide se referme seul après quelques minutes sans toucher la télécommande.
  function touchGuide() {
    clearTimeout(state.guideTimer);
    state.guideTimer = setTimeout(closeGuide, GUIDE_IDLE_MS);
  }

  window.addEventListener('popstate', () => {
    if (state.guide && !(history.state && history.state.guide)) hideGuide();
  });

  function guideKey(k) {
    const sections = guideSections();
    const $content = document.querySelector('.guide-content');
    touchGuide();
    if (k === 'up') state.guide.index = Math.max(0, state.guide.index - 1);
    else if (k === 'down') state.guide.index = Math.min(sections.length - 1, state.guide.index + 1);
    else if (k === 'right') {
      // Fait défiler une rubrique longue ; revient en haut à la fin.
      const atEnd = $content.scrollTop + $content.clientHeight >= $content.scrollHeight - 4;
      $content.scrollTo({ top: atEnd ? 0 : $content.scrollTop + $content.clientHeight * 0.7, behavior: 'smooth' });
      return;
    } else if (k === 'ok') {
      const section = sections[state.guide.index];
      if (section.quiz) location.href = `quiz.html?jeu=${encodeURIComponent(section.quiz)}`;
      return;
    } else if (k === 'left' || k === 'back') {
      closeGuide();
      return;
    } else return;
    renderGuide();
  }

  // ---------- Diaporama ----------
  function shuffle(list) {
    const a = list.slice();
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }

  function showPhoto(step) {
    if (!state.photos.length) return;
    state.photoIndex = (state.photoIndex + step + state.photos.length) % state.photos.length;
    const url = `photo/${encodeURIComponent(state.photos[state.photoIndex])}`;
    const img = new Image();
    img.onload = () => {
      const layers = document.querySelectorAll('.photo');
      state.front = 1 - state.front;
      layers[state.front].style.backgroundImage = `url("${url}")`;
      layers[state.front].classList.add('visible');
      layers[1 - state.front].classList.remove('visible');
    };
    img.src = url;
    // Précharge la suivante.
    new Image().src = `photo/${encodeURIComponent(state.photos[(state.photoIndex + 1) % state.photos.length])}`;
  }

  function startSlideshow(restart) {
    clearInterval(state.photoTimer);
    if (state.photos.length > 1) state.photoTimer = setInterval(() => showPhoto(1), state.data.intervalle * 1000);
    if (restart) showPhoto(1);
  }

  // ---------- Données ----------
  async function refresh() {
    try {
      const res = await fetch('api', { cache: 'no-store' });
      if (!res.ok) throw new Error(res.status);
      const data = await res.json();
      const photosChanged = !state.data || data.photos.join('|') !== state.data.photos.join('|');
      state.data = data;
      state.lang = TEXTS[data.voyageur?.langue] ? data.voyageur.langue : 'fr';
      document.documentElement.lang = state.lang;
      renderWelcome();
      renderClock();
      renderMeteo();
      renderAgenda();
      renderInfos();
      renderNews();
      $('hint').textContent = t().hint;
      if (state.guide) renderGuide();
      if (photosChanged) {
        state.photos = shuffle(data.photos);
        state.photoIndex = -1;
        startSlideshow(true);
      }
    } catch (e) {
      console.warn('Accueil : actualisation impossible', e);
    }
  }

  const KEYS = {
    ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Enter: 'ok',
    Escape: 'back', Backspace: 'back', GoBack: 'back', MediaRewind: 'left', MediaFastForward: 'right',
  };
  const KEYCODES = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 23: 'ok', 27: 'back', 8: 'back', 4: 'back', 227: 'left', 228: 'right' };

  document.addEventListener('keydown', (e) => {
    const k = KEYS[e.key] || KEYCODES[e.keyCode];
    if (!k || !state.data) return;
    if (state.guide) {
      guideKey(k);
    } else if (k === 'ok' || k === 'down') {
      document.body.classList.remove('photo-only');
      openGuide();
    } else if (k === 'up') {
      document.body.classList.toggle('photo-only');
    } else if (k === 'right' || k === 'left') {
      showPhoto(k === 'right' ? 1 : -1);
      startSlideshow(false);
    } else {
      return; // Retour sur l'écran d'accueil : laissé au navigateur
    }
    e.preventDefault();
  });

  // Économiseur d'écran de l'appli Fire TV (?veille=1) : pas de rappel des touches.
  if (new URLSearchParams(location.search).has('veille')) document.body.classList.add('veille');

  setInterval(renderClock, 10 * 1000);
  setInterval(() => {
    if (!state.data) return;
    state.newsIndex++;
    renderNews();
  }, 12 * 1000);
  setInterval(refresh, REFRESH_MS);
  refresh();
})();
