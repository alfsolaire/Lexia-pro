// Lecture d'un agenda iCal (adresse « iCal » d'un agenda Google) sans dépendance.
// Gère : événements datés ou sur la journée, TZID (pris comme l'heure locale du serveur),
// répétitions simples (RRULE FREQ/INTERVAL/COUNT/UNTIL/BYDAY), EXDATE et occurrences modifiées.

const DAY = 24 * 60 * 60 * 1000;
const WEEKDAYS = { SU: 0, MO: 1, TU: 2, WE: 3, TH: 4, FR: 5, SA: 6 };

function unescapeText(s) {
  return s.replace(/\\n/gi, '\n').replace(/\\([,;\\])/g, '$1');
}

function parseLine(line) {
  const colon = line.indexOf(':');
  if (colon < 0) return null;
  const [name, ...params] = line.slice(0, colon).split(';');
  const p = {};
  for (const param of params) {
    const [k, v] = param.split('=');
    if (k) p[k.toUpperCase()] = v;
  }
  return { name: name.toUpperCase(), params: p, value: line.slice(colon + 1) };
}

// "20261027" (journée), "20261027T190000" (heure locale), "20261027T180000Z" (UTC)
function parseIcalDate(value) {
  const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?$/.exec(value.trim());
  if (!m) return null;
  const [, y, mo, d, h, mi, s, z] = m;
  if (h === undefined) return { date: new Date(+y, +mo - 1, +d), allDay: true };
  const date = z ? new Date(Date.UTC(+y, +mo - 1, +d, +h, +mi, +s)) : new Date(+y, +mo - 1, +d, +h, +mi, +s);
  return { date, allDay: false };
}

function parseEvents(text) {
  const lines = text.replace(/\r?\n[ \t]/g, '').split(/\r?\n/);
  const events = [];
  let ev = null;
  for (const raw of lines) {
    if (raw === 'BEGIN:VEVENT') ev = { exdates: [] };
    else if (raw === 'END:VEVENT') {
      if (ev && ev.start) events.push(ev);
      ev = null;
    } else if (ev) {
      const l = parseLine(raw);
      if (!l) continue;
      switch (l.name) {
        case 'UID': ev.uid = l.value; break;
        case 'SUMMARY': ev.summary = unescapeText(l.value); break;
        case 'LOCATION': ev.location = unescapeText(l.value); break;
        case 'STATUS': ev.status = l.value.toUpperCase(); break;
        case 'RRULE': ev.rrule = l.value; break;
        case 'DTSTART': Object.assign(ev, (({ date, allDay }) => ({ start: date, allDay }))(parseIcalDate(l.value) || {})); break;
        case 'DTEND': ev.end = parseIcalDate(l.value)?.date; break;
        case 'RECURRENCE-ID': ev.recurrenceId = parseIcalDate(l.value)?.date; break;
        case 'EXDATE':
          for (const v of l.value.split(',')) {
            const d = parseIcalDate(v);
            if (d) ev.exdates.push(d.date.getTime());
          }
          break;
      }
    }
  }
  return events;
}

function parseRrule(rule) {
  const r = {};
  for (const part of rule.split(';')) {
    const [k, v] = part.split('=');
    r[k.toUpperCase()] = v;
  }
  return {
    freq: r.FREQ,
    interval: Math.max(1, Number(r.INTERVAL) || 1),
    count: r.COUNT ? Number(r.COUNT) : Infinity,
    until: r.UNTIL ? parseIcalDate(r.UNTIL)?.date : null,
    byday: r.BYDAY ? r.BYDAY.split(',').map((d) => WEEKDAYS[d.slice(-2)]).filter((d) => d !== undefined) : null,
  };
}

// Débuts successifs d'un événement répété, jusqu'à `limit`.
function* occurrences(ev, limit) {
  if (!ev.rrule) {
    yield ev.start;
    return;
  }
  const r = parseRrule(ev.rrule);
  const step = (d, n) => {
    const x = new Date(d);
    if (r.freq === 'DAILY') x.setDate(x.getDate() + n);
    else if (r.freq === 'WEEKLY') x.setDate(x.getDate() + 7 * n);
    else if (r.freq === 'MONTHLY') x.setMonth(x.getMonth() + n);
    else if (r.freq === 'YEARLY') x.setFullYear(x.getFullYear() + n);
    else return null;
    return x;
  };
  let emitted = 0;
  for (let i = 0; i < 5000 && emitted < r.count; i++) {
    const base = step(ev.start, i * r.interval);
    if (!base || base > limit || (r.until && base > r.until)) return;
    let candidates = [base];
    if (r.freq === 'WEEKLY' && r.byday) {
      // Jours de la semaine de `base` (semaine commençant le lundi).
      const monday = new Date(base);
      monday.setDate(base.getDate() - ((base.getDay() + 6) % 7));
      candidates = r.byday
        .map((wd) => {
          const d = new Date(monday);
          d.setDate(monday.getDate() + ((wd + 6) % 7));
          return d;
        })
        .filter((d) => d >= ev.start)
        .sort((a, b) => a - b);
    }
    for (const d of candidates) {
      if (emitted >= r.count || d > limit || (r.until && d > r.until)) return;
      emitted++;
      yield d;
    }
  }
}

// Événements qui touchent [from, to[, triés, au format renvoyé à la page.
function eventsBetween(text, from, to) {
  const all = parseEvents(text).filter((e) => e.status !== 'CANCELLED');
  const moved = new Set(all.filter((e) => e.recurrenceId).map((e) => `${e.uid}|${e.recurrenceId.getTime()}`));
  const out = [];
  for (const ev of all) {
    const duration = ev.end ? ev.end - ev.start : ev.allDay ? DAY : 0;
    for (const start of occurrences(ev, to)) {
      const t = start.getTime();
      if (ev.exdates.includes(t) || (!ev.recurrenceId && moved.has(`${ev.uid}|${t}`))) continue;
      const end = new Date(t + duration);
      if (start < to && (end > from || (duration === 0 && start >= from))) {
        out.push({ titre: ev.summary || '(sans titre)', lieu: ev.location || '', debut: start.toISOString(), fin: end.toISOString(), journee: !!ev.allDay });
      }
    }
  }
  return out.sort((a, b) => a.debut.localeCompare(b.debut));
}

module.exports = { parseEvents, eventsBetween, parseIcalDate };
