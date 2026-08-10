/**
 * Részletek-kereszt-ellenőrzés a web-appal — a VÁRT szövegek előállítása.
 *
 * A web-app EREDETI js/ui.js modaltartalom-építőit futtatja Node-ban: a
 * `buildAssetDetails` / `buildPortfolioComposition` és segédeik (pctPlain,
 * compactNum, formatStampHu, defRow, barRow, section) FORRÁSSZÖVEGE innen
 * olvasódik ki — nincs újragépelt másolat. A DOM-ot egy minimális csonk adja
 * (a modal-építők csak createElement/append/textContent szintjét használják),
 * a kész fát pedig sorokká lapítjuk — ugyanabba az alakba, amit az Android
 * `DetailsPresenter` állít elő.
 *
 * MIÉRT ÍGY: a fázis-3 tanulsága (a hu-HU csoportosítás JVM≠ICU eltérése) az
 * volt, hogy a formázást mérni kell, nem feltételezni. Itt ráadásul nem csak a
 * számformátum a kérdés, hanem az is, MELYIK SOR MARAD KI (null-mezők) —
 * pontosan ezt hasonlítja össze a DetailsCrossCheckTest.
 *
 * Futtatás (a repó gyökeréből):
 *   node tools/xcheck-details.mjs
 *
 * Bemenet:  fixtures/details_*.json (valós asset-details válaszok),
 *           fixtures/xcheck_portfolios.json, xcheck_tickers.json,
 *           xcheck_pf_prices.json, xcheck_fx.json
 * Kimenet:  fixtures/xcheck_details_expected.json
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const FIXTURES = resolve(HERE, '..', 'android', 'app', 'src', 'test', 'resources', 'fixtures');
const WEB_DIR = process.env.MONITOR_WEB_DIR
  ? resolve(process.env.MONITOR_WEB_DIR)
  : resolve(HERE, '..', '..', 'IT Reszveny Monitor');

// A dátum-/időbélyeg-formázás zónafüggő; a teszt ugyanezzel a zónával fut.
const TIME_ZONE = 'UTC';
process.env.TZ = TIME_ZONE;

const webModule = (name) => import(pathToFileURL(join(WEB_DIR, 'js', name)).href);
const readFixture = (name) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8').replace(/^﻿/, ''));

const fmt = await webModule('format.js');
const portfolio = await webModule('portfolio.js');
const fx = await webModule('fx.js');

// ---------------------------------------------------------------------------
// A js/ui.js modal-építőinek kiemelése FORRÁSSZÖVEGKÉNT
// ---------------------------------------------------------------------------

const uiSource = readFileSync(join(WEB_DIR, 'js', 'ui.js'), 'utf8');

/** Egy felső szintű deklaráció forrása (a záró `}` a 0. oszlopban van). */
function extract(name) {
  const re = new RegExp(`^(?:const ${name} = [^\\n]*;|function ${name}\\([\\s\\S]*?^\\})`, 'm');
  const hit = uiSource.match(re);
  if (!hit) throw new Error(`Nem található a js/ui.js-ben: ${name} — változott a web-app?`);
  return hit[0];
}

const BUILDER_NAMES = [
  'HU_LOCALE', 'pctPlain', 'compactNum', 'formatStampHu',
  'defRow', 'barRow', 'section', 'buildAssetDetails', 'buildPortfolioComposition',
];
const builderSource = BUILDER_NAMES.map(extract).join('\n\n');

// ---------------------------------------------------------------------------
// Minimális DOM-csonk (csak amit az építők használnak)
// ---------------------------------------------------------------------------

class Node {
  constructor(tag) {
    this.tag = tag;
    this.className = '';
    this.childNodes = [];
    this.style = {};
  }
  appendChild(child) { this.childNodes.push(child); return child; }
  append(...children) { for (const c of children) this.childNodes.push(c); }
  get textContent() {
    if (this._text != null) return this._text + this.childNodes.map((c) => c.textContent).join('');
    return this.childNodes.map((c) => c.textContent).join('');
  }
  set textContent(v) { this._text = String(v); this.childNodes = []; }
  /** Csak osztályszelektor kell (js/ui.js:842 — `.d-bar-val`). */
  querySelector(sel) {
    const cls = sel.replace('.', '');
    for (const c of this.childNodes) {
      if (c.className && c.className.split(/\s+/).includes(cls)) return c;
      const deep = c.querySelector ? c.querySelector(sel) : null;
      if (deep) return deep;
    }
    return null;
  }
}

const documentStub = {
  createElement: (tag) => new Node(tag),
  createDocumentFragment: () => new Node('#fragment'),
  createTextNode: (t) => { const n = new Node('#text'); n.textContent = t; return n; },
};

const builders = new Function(
  'document', 'formatDateHu', 'formatPriceIn',
  `${builderSource}\n return { buildAssetDetails, buildPortfolioComposition };`,
)(documentStub, fmt.formatDateHu, fmt.formatPriceIn);

// ---------------------------------------------------------------------------
// DOM-fa → sorok (ugyanaz az alak, amit az Android-teszt előállít)
// ---------------------------------------------------------------------------

const hasClass = (n, c) => String(n.className || '').split(/\s+/).includes(c);
const ownText = (n) => (n._text != null ? n._text : '');

function flatten(node, out = []) {
  for (const child of node.childNodes) {
    if (hasClass(child, 'd-chips')) {
      out.push(`CHIPS: ${child.childNodes.map((c) => c.textContent).join(' | ')}`);
    } else if (hasClass(child, 'd-def-grid')) {
      for (const row of child.childNodes) {
        const [label, value] = row.childNodes;
        out.push(`DEF: ${label.textContent} = ${value.textContent}`);
      }
    } else if (hasClass(child, 'd-website')) {
      const a = child.childNodes[0];
      out.push(`WEB: ${a.textContent} -> ${a.href}`);
    } else if (hasClass(child, 'd-desc')) {
      out.push(`DESC: ${child.textContent}`);
    } else if (hasClass(child, 'd-section')) {
      out.push(`SECTION: ${child.childNodes[0].textContent}`);
      flatten({ childNodes: child.childNodes.slice(1) }, out);
    } else if (hasClass(child, 'd-bars')) {
      for (const row of child.childNodes) {
        const label = row.childNodes[0];
        const sub = label.querySelector('.d-bar-sub');
        const fill = row.childNodes[1].childNodes[0];
        out.push(
          `BAR: ${ownText(label)}${sub ? ` (${sub.textContent})` : ''}` +
          ` = ${row.childNodes[2].textContent} [${fill.style.width}]`,
        );
      }
    } else if (hasClass(child, 'd-foot')) {
      out.push(`FOOT: ${child.textContent}`);
    }
  }
  return out;
}

/** A modal forrás-lábléce (js/ui.js renderAssetDetails) — ugyanazzal a leképezéssel. */
function sourceFooter(details, cached) {
  const srcMap = { fmp: 'FMP', yahoo: 'Yahoo', alpaca: 'Alpaca' };
  const sources = String(details.source || '').split('+').filter(Boolean).map((s) => srcMap[s] || s);
  const parts = [];
  if (sources.length) parts.push(`Forrás: ${sources.join(' + ')}`);
  if (details.fetchedAt) parts.push(`frissítve: ${formatStampHu(details.fetchedAt)}`);
  if (cached) parts.push('gyorsítótárból');
  return parts.length ? parts.join(' · ') : null;
}
// A formatStampHu-t az építőkkel együtt emeltük ki — külön is kell a lábléchez.
const formatStampHu = new Function(`${extract('HU_LOCALE')}\n${extract('formatStampHu')}\nreturn formatStampHu;`)();

// ---------------------------------------------------------------------------
// 1. Részvény/ETF esetek a VALÓS asset-details válaszokból
// ---------------------------------------------------------------------------

const assetCases = [];
for (const file of ['details_nvda.json', 'details_vwce_de.json', 'details_sxr8_de.json', 'details_wti2_de.json']) {
  const response = readFixture(file);
  const details = response.details;
  assetCases.push({
    fixture: file,
    symbol: details.symbol,
    cached: response.cached,
    lines: flatten(builders.buildAssetDetails(details)),
    footer: sourceFooter(details, response.cached),
  });
}

// ---------------------------------------------------------------------------
// 1.b Szintetikus élesetek — UGYANAZON a webes építőn átvezetve
//
// Két dolgot pinnelnek le, amit a valós válaszok nem fednek le:
// - a hu-HU számformátum határai (csoportosítás 5 jegytől, compact-előléptetés
//   999 999 → „1 M", nulla tizedes elhagyása);
// - a null/üres mezők KIHAGYÁSA (a fázis feladatkiírása: „jobb nem mutatni
//   vagyont, mint rosszat") — üres CEO, hiányzó TER, üres holdings-lista.
// ---------------------------------------------------------------------------

const syntheticCases = [
  {
    name: 'reszveny-szamhatarok',
    details: {
      symbol: 'TEST', profileSymbol: 'TEST', assetType: 'stock', currency: 'USD',
      source: 'fmp', fetchedAt: '2026-01-05T08:07:00.000+00:00',
      profile: {
        sector: 'Technology', industry: null, country: null,
        marketCap: 999999, currency: null, beta: 1.7, employees: 9500,
        ceo: '', ipoDate: null, website: null, description: null,
        alpaca: { exchange: 'NASDAQ', assetClass: null, tradable: false },
      },
      etf: null, holdings: null, sectorWeights: null,
    },
    cached: false,
  },
  {
    name: 'reszveny-csoportositas',
    details: {
      symbol: 'TEST2', profileSymbol: null, assetType: 'stock', currency: 'USD',
      source: '', fetchedAt: null,
      profile: { marketCap: 1500000, beta: 0.5, employees: 12345, alpaca: null },
      etf: null, holdings: null, sectorWeights: null,
    },
    cached: false,
  },
  {
    name: 'etf-ures-mezokkel',
    details: {
      symbol: 'TESTETF', profileSymbol: null, assetType: 'etf', currency: 'EUR',
      source: 'yahoo', fetchedAt: '2026-12-31T23:59:00.000+00:00',
      profile: { sector: null, marketCap: null, alpaca: null },
      etf: { expenseRatio: null, category: null, family: null, netAssets: null, turnover: null },
      holdings: [], sectorWeights: [],
    },
    cached: true,
  },
];

// A szintetikus bemenet BEKERÜL a kimeneti fájlba (`input`), így az Android-teszt
// PONTOSAN ugyanazt az adatot dekódolja — nincs kézzel szinkronban tartott másolat.
for (const c of syntheticCases) {
  assetCases.push({
    fixture: `szintetikus:${c.name}`,
    symbol: c.details.symbol,
    cached: c.cached,
    input: c.details,
    lines: flatten(builders.buildAssetDetails(c.details)),
    footer: sourceFooter(c.details, c.cached),
  });
}

// Időbélyeg-formázás külön is: az egyjegyű óra/nap a kritikus (a JVM
// lokalizált SHORT időformátuma hu-ban „9:03", az Intl 2-digit kérése „09:03").
const stampCases = [
  '2026-08-06T13:03:35.037+00:00',
  '2026-01-05T08:07:00.000+00:00',
  '2026-12-31T23:59:59.000+00:00',
  '2026-03-09T00:00:00.000+00:00',
].map((iso) => ({ iso, text: formatStampHu(iso) }));

// ---------------------------------------------------------------------------
// 2. Portfólió-összetétel a VALÓS portfóliókból (a webes sorrenddel)
// ---------------------------------------------------------------------------

const toRows = (raw) => raw.map((r) => ({
  date: r.date,
  open: Number(r.open),
  high: Number(r.high),
  low: Number(r.low),
  close: Number(r.close),
  volume: Number(r.volume),
}));

fx.setRates(readFixture('xcheck_fx.json'));
const portfolios = readFixture('xcheck_portfolios.json');
const tickers = readFixture('xcheck_tickers.json');
const pricesRaw = readFixture('xcheck_pf_prices.json');
const currencyOf = new Map(tickers.map((t) => [t.symbol, t.currency || 'USD']));
const pricesOf = new Map(Object.entries(pricesRaw).map(([sym, rows]) => [sym, toRows(rows)]));
const tickersBySymbol = new Map(tickers.map((t) => [t.symbol, t]));

const compositionCases = [];
for (const pf of portfolios) {
  const items = pf.portfolio_items || [];
  if (!items.length) continue;
  for (const display of ['USD', 'EUR', 'HUF']) {
    // A web-app openPortfolioDetails-e a MEGJELENÍTÉSI devizára váltott
    // sorokból számol (js/app.js:528-536) — vegyes portfóliónál különben
    // almát adnánk körtéhez a súlyszámításnál.
    const dailyMap = new Map();
    for (const it of items) {
      const rows = pricesOf.get(it.ticker) || [];
      dailyMap.set(it.ticker, fx.convertRows(rows, currencyOf.get(it.ticker) || 'USD', display));
    }
    const comp = portfolio.computeComposition(items, dailyMap);
    compositionCases.push({
      portfolioId: pf.id,
      portfolioName: pf.name,
      displayCurrency: display,
      lines: flatten(builders.buildPortfolioComposition(comp, tickersBySymbol, display)),
    });
  }
}

const target = join(FIXTURES, 'xcheck_details_expected.json');
writeFileSync(
  target,
  JSON.stringify(
    {
      note: 'A web-app js/ui.js modal-építőivel generált várt szövegek — ne szerkeszd kézzel (tools/xcheck-details.mjs).',
      generatedAt: new Date().toISOString(),
      timeZone: TIME_ZONE,
      assetCases,
      stampCases,
      compositionCases,
    },
    null,
    2,
  ),
  'utf8',
);
console.log(`${assetCases.length} profil- és ${compositionCases.length} összetétel-eset kiírva: ${target}`);
