/**
 * Portfólió-kereszt-ellenőrzés a web-appal — a VÁRT értékek előállítása.
 *
 * A web-app EREDETI js/portfolio.js + js/fx.js moduljait futtatja Node-ban, a
 * web-app app.js-ének SORRENDJÉVEL (közös tengely → előre-töltés NATÍV devizában
 * → átváltás), ugyanazokra a valós portfóliókra és tagsorokra, amiket az
 * Android-teszt is használ.
 *
 * Futtatás (a repó gyökeréből):
 *   node tools/xcheck-portfolio.mjs
 *
 * Bemenet:  fixtures/xcheck_portfolios.json, xcheck_tickers.json,
 *           xcheck_pf_prices.json, xcheck_fx.json
 * Kimenet:  fixtures/xcheck_portfolio_expected.json
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

const portfolio = await webModule('portfolio.js');
const fx = await webModule('fx.js');
const { presetRange, computeStats } = await webModule('transform.js');
const fmt = await webModule('format.js');

const readFixture = (name) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8').replace(/^﻿/, ''));

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

const CURRENCIES = ['USD', 'EUR', 'HUF'];
const TODAY = new Date().toISOString().slice(0, 10);

const cases = [];

for (const pf of portfolios) {
  const items = pf.portfolio_items || [];
  if (!items.length) continue;

  for (const display of CURRENCIES) {
    // === A web-app app.js SORRENDJE (5. invariáns) ===========================
    // 1. közös dátumtengely a NATÍV sorokból
    const members = items
      .map((it) => ({
        ticker: it.ticker,
        rows: pricesOf.get(it.ticker) || [],
        currency: currencyOf.get(it.ticker) || 'USD',
      }))
      .filter((m) => m.rows.length);
    if (!members.length) continue;

    const axis = portfolio.commonAxis(members.map((m) => m.rows));

    // 2. előre-töltés NATÍV devizában, 3. CSAK EZUTÁN átváltás
    const dailyMap = new Map();
    for (const m of members) {
      dailyMap.set(m.ticker, fx.convertRows(portfolio.fillForward(m.rows, axis), m.currency, display));
    }

    const series = portfolio.buildPortfolioSeries(items, dailyMap);

    // A bekerülési ár a VÁSÁRLÁS NAPI árfolyamán vált át (7. invariáns).
    const costed = items.map((it) => {
      if (it.purchase_price == null) return it;
      const from = currencyOf.get(it.ticker) || 'USD';
      if (from === display) return it;
      const price = fx.convertValue(it.purchase_price, from, display, it.purchase_date || TODAY);
      return { ...it, purchase_price: price };
    });
    const pnl = portfolio.computePnL(costed, dailyMap);

    const composition = portfolio.computeComposition(items, dailyMap);

    // Stat-kártyák a MIND ablakra (a portfólió-nézet is a napi sorokból számol).
    const { from, to } = presetRange(series, 'MIND');
    const stats = computeStats(series, from, to);

    cases.push({
      portfolioId: pf.id,
      portfolioName: pf.name,
      displayCurrency: display,
      itemCount: items.length,
      seriesLength: series.length,
      firstDate: series[0]?.date ?? null,
      lastDate: series[series.length - 1]?.date ?? null,
      totalValue: stats.lastClose,
      periodChangePct: stats.periodChangePct,
      periodHigh: stats.periodHigh,
      periodLow: stats.periodLow,
      pnl: {
        pnl: pnl.pnl,
        pnlPct: pnl.pnlPct,
        costedCount: pnl.costedCount,
        totalCount: pnl.totalCount,
      },
      formatted: {
        totalValue: fmt.formatPriceIn(stats.lastClose, display),
        pnl: Number.isFinite(pnl.pnl)
          ? `${fmt.formatSignedIn(pnl.pnl, display)} (${fmt.formatPct(pnl.pnlPct)})`
          : '—',
      },
      composition: {
        totalValue: composition.totalValue,
        pricedCount: composition.pricedCount,
        rows: composition.rows.map((r) => ({ ticker: r.ticker, value: r.value, weight: r.weight })),
      },
    });
  }
}

const target = join(FIXTURES, 'xcheck_portfolio_expected.json');
writeFileSync(
  target,
  JSON.stringify(
    {
      note: 'A web-app js/portfolio.js + fx.js moduljaival generált várt értékek — ne szerkeszd kézzel (tools/xcheck-portfolio.mjs).',
      generatedAt: new Date().toISOString(),
      cases,
    },
    null,
    2,
  ),
  'utf8',
);
console.log(`${cases.length} eset kiírva: ${target}`);
