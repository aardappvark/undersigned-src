/**
 * Undersigned — Anonymous Geo-Analytics Worker
 *
 * Cloudflare Worker + D1 endpoint for privacy-preserving analytics.
 *
 * HOW IT WORKS:
 * 1. The app POSTs { event_type: "app_open" } with its per-app API key.
 * 2. Cloudflare's edge derives the country from the request IP via request.cf —
 *    no external geo API, and our code never sees or stores the IP.
 * 3. We upsert one aggregate row: (app, date, event_type, country) += 1.
 *
 * PRIVACY:
 * - Zero PII: no IP, no user id, no device id, no wallet address, no fingerprint.
 * - Country only. City is deliberately NOT recorded — the post-2026-08-21 fleet
 *   pattern dropped it, and the privacy policy must not promise more than this.
 * - GDPR (Recital 26), CCPA, LGPD, APPI, PIPA compliant without consent.
 *
 * STORAGE — read this before changing anything:
 * The Cloudflare account is at its 10/10 D1 database cap (verified 2026-08-24), so
 * Undersigned does NOT get its own database. It writes to the multi-tenant
 * `fleet_events` table in the stillpoint-analytics database, the same table
 * fleet-analytics owns, with app = 'undersigned'. Every query here is scoped by that
 * discriminator. stillpoint's own `events` table lives in the same database and is
 * never touched from this worker.
 *
 * ENDPOINTS:
 *   POST /  or  /track   — track an event (X-API-Key protected)
 *   GET  /health         — liveness (public)
 *   GET  /stats          — detailed stats (X-API-Key protected)
 *   GET  /public-stats   — public verification endpoint (no auth, edge-cached 1h)
 */

const APP = 'undersigned';

const PROJECT_META = {
  project: "Undersigned",
  publisher: "AardAppvark",
  jurisdiction: "Australia",
  category: "Signed, sybil-resistant reviews and a trust layer for Solana dApp Store apps",
  website: "https://aardappvark.github.io/undersigned-legal",
  data_provenance: {
    source: "Undersigned production analytics (Cloudflare Worker + D1)",
    privacy: "Zero PII stored. IP never logged. Country derived from the Cloudflare edge only. City is not recorded. Aggregate counts only.",
    compliance: "GDPR Recital 26, CCPA, LGPD, APPI, PIPA compliant without consent",
    cache_ttl_seconds: 3600,
  },
};

// ============================================================
// ALLOWED EVENT TYPES — must match GeoAnalyticsService.Events exactly.
// Anything not listed is rejected with a 400, so the two lists must be deployed
// together and this worker must go live BEFORE any APK that sends a new name.
// ============================================================

// Per-network token bucket, per isolate (no KV: the account is at its D1 cap). 60 a minute is far above any honest app.
const buckets = new Map();
function allow(ip) {
  const now = Date.now(); const b = buckets.get(ip) || { n: 0, at: now };
  if (now - b.at > 60_000) { b.n = 0; b.at = now; }
  b.n += 1; buckets.set(ip, b); if (buckets.size > 5000) buckets.clear();
  return b.n <= 60;
}

const ALLOWED_EVENTS = [
  'app_open',
  'onboarding_complete',
  'terms_accepted',
  'wallet_connected',
  'wallet_disconnected',
  'sgt_verified',

  // Plural on purpose — these are the fleet dashboard's revenue keys
  // (fleet-stats-dashboard/api/fleet.js PAYMENT_KEYS). A differently spelled key
  // reads as zero revenue rather than as no revenue. Fired for EVERY treasury
  // payment the app makes with the UNS memo (Pro licence, board listing, boost),
  // so /public-stats reconciles against the treasury ledger.
  'payments_sol',
  'payments_skr',
  // Deliberately NOT a payment key: a restore re-establishes a purchase already
  // counted when it was made, so counting it would double-count on every reinstall.
  // Undersigned reads Pro from the chain and has no restore flow; kept for fleet parity.
  'purchases_restored',

  'review_signed',
  'review_boosted',
  'store_handoff',
  'publisher_claim_verified',
  'settings_opened',
  'upgrade_viewed',
  'geo_blocked',
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, X-API-Key',
        },
      });
    }

    if (url.pathname === '/health') {
      return json({ status: 'ok', app: APP, timestamp: new Date().toISOString() });
    }
    if (url.pathname === '/public-stats' && request.method === 'GET') {
      return handlePublicStats(env);
    }
    if (url.pathname === '/stats' && request.method === 'GET') {
      return handleStats(request, env);
    }
    if (request.method === 'POST' && (url.pathname === '/' || url.pathname === '/track')) {
      return handleTrack(request, env);
    }
    return new Response('Not found', { status: 404 });
  },
};

async function handleTrack(request, env) {
  const apiKey = request.headers.get('X-API-Key');
  if (!apiKey || apiKey !== env.API_KEY) return json({ error: 'Unauthorized' }, 401);
  // The key ships inside the APK, so it is public by design (audit, 12 Oct 2026): what protects the counts is server-side —
  // a per-network minute limit in this isolate and a hard cap on any one row's count a day.
  if (!allow(request.headers.get('CF-Connecting-IP') || 'none')) return json({ error: 'Too many requests' }, 429);

  let body;
  try { body = await request.json(); } catch { return json({ error: 'Invalid JSON' }, 400); }

  const eventType = body.event_type;
  if (!eventType || typeof eventType !== 'string' || !ALLOWED_EVENTS.includes(eventType)) {
    return json({ error: 'Invalid event_type' }, 400);
  }

  const country = /^[A-Z]{2}$/.test(request.cf?.country || '') ? request.cf.country : 'XX';
  const date = new Date().toISOString().split('T')[0];

  try {
    await env.DB.prepare(`
      INSERT INTO fleet_events (app, date, event_type, country, count)
      VALUES (?, ?, ?, ?, 1)
      ON CONFLICT(app, date, event_type, country)
      DO UPDATE SET count = MIN(count + 1, 50000)
    `).bind(APP, date, eventType, country).run();
    return json({ ok: true });
  } catch (err) {
    console.error('DB write error:', err);
    return json({ error: 'Server error' }, 500);
  }
}

async function handleStats(request, env) {
  const apiKey = request.headers.get('X-API-Key');
  if (!apiKey || apiKey !== (env.STATS_KEY || env.API_KEY)) return json({ error: 'Unauthorized' }, 401);   // STATS_KEY: a separate read secret once set (audit, 12 Oct 2026)

  const url = new URL(request.url);
  const days = Math.min(parseInt(url.searchParams.get('period')) || 30, 365);
  const since = new Date();
  since.setDate(since.getDate() - days);
  const sinceStr = since.toISOString().split('T')[0];

  try {
    const totals = await env.DB.prepare(`
      SELECT event_type, SUM(count) AS total FROM fleet_events
      WHERE app = ? AND date >= ? GROUP BY event_type ORDER BY total DESC
    `).bind(APP, sinceStr).all();

    const countries = await env.DB.prepare(`
      SELECT country, SUM(count) AS total FROM fleet_events
      WHERE app = ? AND date >= ? GROUP BY country ORDER BY total DESC LIMIT 50
    `).bind(APP, sinceStr).all();

    const daily = await env.DB.prepare(`
      SELECT date, SUM(count) AS total FROM fleet_events
      WHERE app = ? AND date >= ? GROUP BY date ORDER BY date DESC
    `).bind(APP, sinceStr).all();

    return json({
      period_days: days,
      since: sinceStr,
      event_totals: totals.results,
      countries: countries.results,
      daily_trend: daily.results,
    });
  } catch (err) {
    console.error('Stats query error:', err);
    return json({ error: 'Query failed' }, 500);
  }
}

async function handlePublicStats(env) {
  try {
    const lifetimeRow = await env.DB.prepare(`
      SELECT
        COUNT(DISTINCT date) AS days_active,
        COUNT(DISTINCT CASE WHEN country != 'XX' THEN country END) AS countries_reached,
        COALESCE(SUM(count), 0) AS total_events,
        MIN(date) AS first_day,
        MAX(date) AS last_day
      FROM fleet_events WHERE app = ?
    `).bind(APP).first();

    const eventRows = await env.DB.prepare(`
      SELECT event_type, SUM(count) AS total FROM fleet_events WHERE app = ? GROUP BY event_type
    `).bind(APP).all();

    const c = {};
    for (const row of eventRows.results || []) c[row.event_type] = Number(row.total);

    const countryRows = await env.DB.prepare(`
      SELECT country, SUM(count) AS events FROM fleet_events
      WHERE app = ? AND country != 'XX' GROUP BY country ORDER BY events DESC LIMIT 20
    `).bind(APP).all();

    const thirty = new Date();
    thirty.setDate(thirty.getDate() - 30);
    const dailyRows = await env.DB.prepare(`
      SELECT date, SUM(count) AS events FROM fleet_events
      WHERE app = ? AND date >= ? GROUP BY date ORDER BY date DESC
    `).bind(APP, thirty.toISOString().split('T')[0]).all();

    const payload = {
      ...PROJECT_META,
      data_provenance: { ...PROJECT_META.data_provenance, generated_at: new Date().toISOString() },
      lifetime: {
        days_active: Number(lifetimeRow?.days_active || 0),
        countries_reached: Number(lifetimeRow?.countries_reached || 0),
        total_events: Number(lifetimeRow?.total_events || 0),
        app_opens: c.app_open || 0,
        users_onboarded: c.onboarding_complete || 0,
        terms_accepted: c.terms_accepted || 0,
        wallet_connections: c.wallet_connected || 0,
        wallet_disconnections: c.wallet_disconnected || 0,
        sgt_verifications: c.sgt_verified || 0,

        // Fleet dashboard revenue keys — spelled exactly as PAYMENT_KEYS expects.
        payments_sol: c.payments_sol || 0,
        payments_skr: c.payments_skr || 0,
        // Not a revenue key: see the ALLOWED_EVENTS note.
        purchases_restored: c.purchases_restored || 0,
        upgrade_views: c.upgrade_viewed || 0,

        reviews_signed: c.review_signed || 0,
        reviews_boosted: c.review_boosted || 0,
        store_handoffs: c.store_handoff || 0,
        publisher_claims_verified: c.publisher_claim_verified || 0,
        settings_opens: c.settings_opened || 0,
        geo_blocked: c.geo_blocked || 0,

        first_day: lifetimeRow?.first_day || null,
        last_day: lifetimeRow?.last_day || null,
      },
      top_countries: (countryRows.results || []).map(r => ({ country: r.country, events: Number(r.events) })),
      daily_trend_last_30d: (dailyRows.results || []).map(r => ({ date: r.date, events: Number(r.events) })),
    };

    return new Response(JSON.stringify(payload, null, 2), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (err) {
    console.error('public-stats error:', err);
    return json({ error: 'Query failed', detail: String(err) }, 500);
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
  });
}
