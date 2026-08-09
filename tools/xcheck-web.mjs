/**
 * Kereszt-ellenőrzés a web-appal — a VÁRT értékek előállítása.
 *
 * A web-app EREDETI moduljait (js/transform.js, js/fx.js, js/format.js) futtatja
 * Node-ban ugyanazokra a valós sorokra, amiket az Android-teszt is használ, és
 * kiírja a stat-kártyák nyers és formázott értékeit. A Kotlin oldalon a
 * WebCrossCheckTest ugyanezt számolja a domain-porttal, és karakterre veti össze.
 *
 * Futtatás (a repó gyökeréből):
 *   node tools/xcheck-web.mjs
 *
 * Bemenet:  android/app/src/test/resources/fixtures/xcheck_*.json
 * Kimenet:  android/app/src/test/resources/fixtures/xcheck_expected.json
 *
 * A web-app helyét a MONITOR_WEB_DIR környezeti változó írja felül.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const FIXTURES = resolve(HERE, '..', 'android', 'app', 'src', 'test', 'resources', 'fixtures');
const WEB_DIR = process.env.MONITOR_WEB_DIR
  ? resolve(process.env.MONITOR_WEB_DIR)
  : resolve(HERE, '..', '..', 'IT Reszveny Monitor');

const webModule = (name) => import(pathToFileURL(join(WEB_DIR, 'js', name)).href);

const { aggregate, presetRange, computeStats } = await webModule('transform.js');
const fx = await webModule('fx.js');
const fmt = await webModule('format.js');

const readFixture = (name) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8').replace(/^﻿/, ''));

// A PostgREST numeric oszlopai stringként is jöhetnek — a web-app normalizeRow-ja
// Number()-rel alakítja; itt ugyanaz történik.
const toRows = (raw) => raw.map((r) => ({
  date: r.date,
  open: Number(r.open),
  high: Number(r.high),
  low: Number(r.low),
  close: Number(r.close),
  volume: Number(r.volume),
}));

fx.setRates(readFixture('xcheck_fx.json'));

const INSTRUMENTS = [
  { ticker: 'NVDA', nativeCurrency: 'USD', file: 'xcheck_nvda.json' },
  { ticker: 'SXR8.DE', nativeCurrency: 'EUR', file: 'xcheck_sxr8de.json' },
  { ticker: 'AIAG.L', nativeCurrency: 'GBp', file: 'xcheck_aiagl.json' },
];
const PRESETS = ['1HET', '1M', '3M', '6M', 'YTD', '1EV', 'MIND'];
const CURRENCIES = ['USD', 'EUR', 'HUF'];
const RESOLUTIONS = { daily: 'daily', weekly: 'weekly', monthly: 'monthly' };

const cases = [];

for (const inst of INSTRUMENTS) {
  const native = toRows(readFixture(inst.file));

  for (const display of CURRENCIES) {
    // A web-app toDisplay()-e: a NATÍV sorokat váltja át naponkénti árfolyamon.
    const daily = fx.convertRows(native, inst.nativeCurrency, display);

    // Aggregáció-összevetés (a grafikon bemenete) — felbontásonként.
    const bars = {};
    for (const [key, mode] of Object.entries(RESOLUTIONS)) {
      const list = aggregate(daily, mode);
      const last = list[list.length - 1] || null;
      bars[key] = {
        count: list.length,
        lastDate: last ? last.date : null,
        lastOpen: last ? last.open : null,
        lastHigh: last ? last.high : null,
        lastLow: last ? last.low : null,
        lastClose: last ? last.close : null,
        lastVolume: last ? last.volume : null,
      };
    }

    for (const preset of PRESETS) {
      const { from, to } = presetRange(daily, preset);
      const s = computeStats(daily, from, to);

      // A stat-kártyák szövege pontosan úgy, ahogy az ui.js setStats összerakja.
      const dayText = Number.isFinite(s.dayChange)
        ? `${fmt.formatSignedIn(s.dayChange, display)} (${fmt.formatPct(s.dayChangePct)})`
        : '—';

      cases.push({
        ticker: inst.ticker,
        nativeCurrency: inst.nativeCurrency,
        displayCurrency: display,
        preset,
        rowCount: daily.length,
        from,
        to,
        stats: {
          lastClose: s.lastClose,
          dayChange: s.dayChange,
          dayChangePct: s.dayChangePct,
          periodChangePct: s.periodChangePct,
          periodHigh: s.periodHigh,
          periodLow: s.periodLow,
          avgVolume: s.avgVolume,
        },
        formatted: {
          lastClose: fmt.formatPriceIn(s.lastClose, display),
          dayChange: dayText,
          periodChangePct: fmt.formatPct(s.periodChangePct),
          periodHigh: fmt.formatPriceIn(s.periodHigh, display),
          periodLow: fmt.formatPriceIn(s.periodLow, display),
          avgVolume: fmt.formatVolume(s.avgVolume),
          lastDate: fmt.formatDateHu(to),
        },
        bars,
      });
    }
  }
}

const out = {
  note: 'A web-app js/ moduljaival generált várt értékek — ne szerkeszd kézzel (tools/xcheck-web.mjs).',
  generatedAt: new Date().toISOString(),
  webDir: WEB_DIR,
  cases,
};

const target = join(FIXTURES, 'xcheck_expected.json');
writeFileSync(target, JSON.stringify(out, null, 2), 'utf8');
console.log(`${cases.length} eset kiírva: ${target}`);
