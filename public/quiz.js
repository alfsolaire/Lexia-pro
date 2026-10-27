// Quiz en famille à la télécommande : quiz.html?jeu=lisieux charge quiz-lisieux.json.
// Les joueurs répondent chacun leur tour. ◀ ▶ ▲ ▼ choisir · OK valider · Retour quitter.

(() => {
  const esc = Lexia.escapeHtml;
  const $root = document.getElementById('quiz');
  const LETTERS = ['A', 'B', 'C', 'D'];
  const MAX_PLAYERS = 6;

  const jeu = new URLSearchParams(location.search).get('jeu') || 'lisieux';
  const game = { data: null, players: 2, scores: [], questions: [], index: 0, focus: 0, answered: null, phase: 'setup' };

  function shuffle(list) {
    const a = list.slice();
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }

  function playerName(i) {
    return game.players === 1 ? 'Vous' : `Joueur ${i + 1}`;
  }

  function currentPlayer() {
    return game.index % game.players;
  }

  function start() {
    const per = Math.min(game.data.parPartie || 10, game.data.questions.length);
    // Autant de questions pour chacun : arrondi au multiple du nombre de joueurs.
    const count = Math.max(game.players, Math.min(game.data.questions.length, Math.round(per / game.players) * game.players));
    game.questions = shuffle(game.data.questions).slice(0, count).map((q) => {
      const order = shuffle(q.choix.map((_, i) => i));
      return { ...q, choix: order.map((i) => q.choix[i]), bonne: order.indexOf(q.bonne) };
    });
    game.scores = Array(game.players).fill(0);
    game.index = 0;
    game.focus = 0;
    game.answered = null;
    game.phase = 'question';
    render();
  }

  // ---------- Écrans ----------
  function renderSetup() {
    $root.innerHTML = `
      <section class="setup">
        <div class="logo">🎲</div>
        <h1>${esc(game.data.titre)}</h1>
        <p class="sub">${esc(game.data.sousTitre || '')}</p>
        <div class="players">
          <span class="arrow">◀</span>
          <strong>${game.players}</strong>
          <span class="arrow">▶</span>
        </div>
        <p class="sub">${game.players === 1 ? 'joueur' : 'joueurs'} · chacun répond à son tour</p>
        <p class="hint">◀ ▶ nombre de joueurs · OK commencer · Retour quitter</p>
      </section>`;
  }

  function renderScores() {
    if (game.players === 1) return `<div class="scores"><span class="score current">${game.scores[0]} pt${game.scores[0] > 1 ? 's' : ''}</span></div>`;
    return `<div class="scores">${game.scores.map((s, i) => `<span class="score ${i === currentPlayer() ? 'current' : ''}">${esc(playerName(i))} <b>${s}</b></span>`).join('')}</div>`;
  }

  function renderQuestion() {
    const q = game.questions[game.index];
    const done = game.answered !== null;
    $root.innerHTML = `
      <header class="bar">
        <span class="count">Question ${game.index + 1} / ${game.questions.length}</span>
        ${renderScores()}
      </header>
      ${game.players > 1 ? `<div class="turn">À ${esc(playerName(currentPlayer()))} de jouer</div>` : ''}
      <h2 class="question">${esc(q.q)}</h2>
      <div class="answers">
        ${q.choix.map((c, i) => {
          const cls = [
            'answer',
            !done && i === game.focus ? 'focused' : '',
            done && i === q.bonne ? 'right' : '',
            done && i === game.answered && i !== q.bonne ? 'wrong' : '',
          ].join(' ');
          return `<button class="${cls}" data-i="${i}"><span class="letter">${LETTERS[i]}</span>${esc(c)}</button>`;
        }).join('')}
      </div>
      <footer class="feedback">
        ${done ? `
          <p class="verdict ${game.answered === q.bonne ? 'ok' : 'ko'}">${game.answered === q.bonne ? 'Bonne réponse !' : `Raté… c'était ${LETTERS[q.bonne]}.`}</p>
          ${q.info ? `<p class="info">${esc(q.info)}</p>` : ''}
          <p class="hint">OK ${game.index + 1 < game.questions.length ? 'question suivante' : 'voir les résultats'}</p>`
        : '<p class="hint">◀ ▶ ▲ ▼ choisir · OK valider</p>'}
      </footer>`;
    $root.querySelectorAll('.answer').forEach((el) => {
      el.addEventListener('click', () => {
        if (game.answered !== null) return next();
        game.focus = Number(el.dataset.i);
        answer();
      });
    });
  }

  function renderEnd() {
    const ranking = game.scores.map((s, i) => ({ s, i })).sort((a, b) => b.s - a.s);
    const best = ranking[0].s;
    const perPlayer = game.questions.length / game.players;
    const title = game.players === 1
      ? (best === perPlayer ? 'Sans faute !' : best >= perPlayer * 0.7 ? 'Bravo !' : 'Bien joué !')
      : ranking.filter((r) => r.s === best).length > 1 ? 'Égalité !' : `${playerName(ranking[0].i)} gagne !`;
    $root.innerHTML = `
      <section class="end">
        <div class="logo">🏆</div>
        <h1>${esc(title)}</h1>
        <ol class="ranking">
          ${ranking.map((r) => `<li class="${r.s === best ? 'top' : ''}"><span>${esc(playerName(r.i))}</span><b>${r.s} / ${perPlayer}</b></li>`).join('')}
        </ol>
        <p class="hint">OK rejouer · Retour quitter</p>
      </section>`;
  }

  function render() {
    if (game.phase === 'setup') renderSetup();
    else if (game.phase === 'question') renderQuestion();
    else renderEnd();
  }

  // ---------- Jeu ----------
  function answer() {
    const q = game.questions[game.index];
    game.answered = game.focus;
    if (game.answered === q.bonne) game.scores[currentPlayer()]++;
    render();
  }

  function next() {
    game.index++;
    game.answered = null;
    game.focus = 0;
    if (game.index >= game.questions.length) game.phase = 'end';
    render();
  }

  function leave() {
    if (history.length > 1) history.back();
    else location.href = location.pathname.includes('/accueil/') ? './' : '/';
  }

  function onKey(k) {
    if (k === 'back') return leave();
    if (game.phase === 'setup') {
      if (k === 'left' || k === 'down') game.players = Math.max(1, game.players - 1);
      else if (k === 'right' || k === 'up') game.players = Math.min(MAX_PLAYERS, game.players + 1);
      else if (k === 'ok') return start();
      return render();
    }
    if (game.phase === 'end') {
      if (k === 'ok') {
        game.phase = 'setup';
        render();
      }
      return;
    }
    if (game.answered !== null) {
      if (k === 'ok') next();
      return;
    }
    // Réponses en grille 2 × 2 : A B / C D.
    const moves = { left: -1, right: 1, up: -2, down: 2 };
    if (k in moves) {
      const n = game.focus + moves[k];
      if (n >= 0 && n < 4 && !((k === 'left' && game.focus % 2 === 0) || (k === 'right' && game.focus % 2 === 1))) game.focus = n;
      render();
    } else if (k === 'ok') {
      answer();
    }
  }

  const KEYS = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Enter: 'ok', Escape: 'back', Backspace: 'back', GoBack: 'back' };
  const KEYCODES = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 23: 'ok', 27: 'back', 8: 'back', 4: 'back' };
  document.addEventListener('keydown', (e) => {
    const k = KEYS[e.key] || KEYCODES[e.keyCode];
    if (!k) return;
    e.preventDefault();
    onKey(k);
  });

  async function boot() {
    if (!/^[a-z0-9-]+$/.test(jeu)) throw new Error('Quiz inconnu');
    const res = await fetch(`quiz-${jeu}.json`, { cache: 'no-cache' });
    if (!res.ok) throw new Error('Quiz introuvable');
    game.data = await res.json();
    document.title = game.data.titre;
    render();
  }

  boot().catch((e) => {
    $root.innerHTML = `<section class="setup"><h1>${esc(e.message)}</h1><p class="hint">Retour pour quitter</p></section>`;
  });
})();
