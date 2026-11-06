import type { TrackingEntry } from '../types/trackingData';

// A 2D nézet játékos-pontjainak simítása. A backend a doboz alsó közepét képkockánként, simítás nélkül vetíti a
// pályára, ezért a pontok remegnek (a doboz ~0,5 px-es zaját a perspektíva nagyítja; lásd a
// docs/ai-collaboration-log.md 2026-10-04-i és 2026-11-06-i bejegyzéseit).
//
// Módszer: trackenként (`player_id`), Gauss-súlyozott ELSŐFOKÚ lokális illesztés. Minden észlelt képkockában a
// környező pontokra (Gauss-súllyal, a képkocka-távolság szerint) egyenest illesztünk, és az egyenes értékét
// vesszük a képkocka helyén.
//  - Az adat offline készül, a teljes track megvan, ezért a szűrő előre-hátra néz (nem-kauzális): nem késik.
//  - A súly a tényleges képkocka-távolságtól függ, nem a minta sorszámától, ezért a hiányzó képkockákat külön
//    interpoláció nélkül kezeli.
//  - Az elsőfokú illesztés (a sima súlyozott átlaggal szemben) egyenletes mozgásnál torzításmentes, a track szélén
//    is: egy sima átlag a féloldalas ablak miatt a track belseje felé húzna.
//  - Id-k között NEM átlagol: más `player_id` más track. A tracker id-váltásai (új id = új track) így nem simulnak
//    össze; a pont ilyenkor eltűnik és újra megjelenik (erre külön megoldás kell, nem a simítás dolga).
//
// A paraméter másodpercben van megadva, hogy más fps-nél (vagy más EMISSION_INTERVAL-nál) is ugyanazt jelentse.

export interface SmoothingOptions {
  // A Gauss-mag szórása másodpercben. Nagyobb = simább, de a valódi irányváltásokat is jobban lekerekíti.
  readonly sigmaSeconds: number;
}

// Az alapérték a hallgató döntése (2026-11-06): a valódi felvételen, lejátszásban 0,25 s tűnt simának. A mérés
// (scripts/measureSmoothing.mts, docs/ai-collaboration-log.md) szerint a kockánkénti zaj csökkenése már kb. 0,10 s-nál
// telítődik, efölött a simítás ára nő: a pontok medián elmozdítása 0,12 (0,10 s) → 0,21 egység (0,25 s), és a
// fordulók jobban lekerekednek. Ha a mozgás lomhának tűnik, kisebb értékkel élesebbé tehető.
export const DEFAULT_SMOOTHING: SmoothingOptions = { sigmaSeconds: 0.25 };

// Az ablak félszélessége a szórás többszöröse: ennél távolabb a Gauss-súly (e^-4,5 ≈ 1%) már elhanyagolható.
const REACH_IN_SIGMAS = 3;

// Az illesztés elfajulásának küszöbe (egyetlen pont az ablakban): ilyenkor a súlyozott átlagra esünk vissza.
const DEGENERATE_RATIO = 1e-9;

interface Series {
  // Képkocka szerint növekvő sorrendben.
  readonly rows: number[]; // az eredeti tömb indexei
  readonly frames: number[];
  readonly xs: number[];
  readonly ys: number[];
}

function hasPosition(entry: TrackingEntry): entry is TrackingEntry & { tx: number; ty: number } {
  return (
    entry.player_id >= 0 &&
    typeof entry.tx === 'number' &&
    typeof entry.ty === 'number' &&
    Number.isFinite(entry.tx) &&
    Number.isFinite(entry.ty)
  );
}

function groupByTrack(entries: readonly TrackingEntry[]): Series[] {
  const byId = new Map<number, number[]>();
  entries.forEach((entry, index) => {
    if (!hasPosition(entry)) return;
    const bucket = byId.get(entry.player_id);
    if (bucket) bucket.push(index);
    else byId.set(entry.player_id, [index]);
  });

  const series: Series[] = [];
  for (const indices of byId.values()) {
    indices.sort((a, b) => entries[a].frame - entries[b].frame);
    series.push({
      rows: indices,
      frames: indices.map((i) => entries[i].frame),
      xs: indices.map((i) => entries[i].tx as number),
      ys: indices.map((i) => entries[i].ty as number),
    });
  }
  return series;
}

// A Gauss-mag értékei egész képkocka-távolságokra (a képkocka-indexek egészek), hogy ne kelljen minden pontnál exp().
function gaussianKernel(sigmaFrames: number, reach: number): Float64Array {
  const kernel = new Float64Array(reach + 1);
  for (let d = 0; d <= reach; d++) kernel[d] = Math.exp(-(d * d) / (2 * sigmaFrames * sigmaFrames));
  return kernel;
}

function smoothSeries(series: Series, sigmaFrames: number): { xs: Float64Array; ys: Float64Array } {
  const { frames, xs, ys } = series;
  const n = frames.length;
  const outX = new Float64Array(n);
  const outY = new Float64Array(n);
  const reach = Math.max(1, Math.ceil(REACH_IN_SIGMAS * sigmaFrames));
  const kernel = gaussianKernel(sigmaFrames, reach);

  let lo = 0;
  let hi = 0;
  for (let i = 0; i < n; i++) {
    const center = frames[i];
    while (frames[lo] < center - reach) lo++;
    while (hi < n && frames[hi] <= center + reach) hi++;

    // Súlyozott legkisebb négyzetek: v ≈ a + b·d, ahol d = a képkocka eltérése a középponttól. Az `a` az eredmény.
    let s0 = 0;
    let s1 = 0;
    let s2 = 0;
    let tx0 = 0;
    let tx1 = 0;
    let ty0 = 0;
    let ty1 = 0;
    for (let j = lo; j < hi; j++) {
      const d = frames[j] - center;
      const w = kernel[Math.abs(d)];
      const wd = w * d;
      s0 += w;
      s1 += wd;
      s2 += wd * d;
      tx0 += w * xs[j];
      tx1 += wd * xs[j];
      ty0 += w * ys[j];
      ty1 += wd * ys[j];
    }

    const det = s0 * s2 - s1 * s1;
    if (det > DEGENERATE_RATIO * s0 * s2) {
      outX[i] = (s2 * tx0 - s1 * tx1) / det;
      outY[i] = (s2 * ty0 - s1 * ty1) / det;
    } else {
      // Egyetlen pont az ablakban: nincs mit illeszteni, a súlyozott átlag maga a pont.
      outX[i] = tx0 / s0;
      outY[i] = ty0 / s0;
    }
  }
  return { xs: outX, ys: outY };
}

// Az eredmény ugyanolyan hosszú és sorrendű, mint a bemenet. A `tx`/`ty` nélküli vagy id nélküli (player_id < 0) sor
// változatlanul (ugyanaz az objektum) kerül át; a simított soron a nyers érték a `rawTx`/`rawTy`-ban marad, a doboz
// (x1..y2) és a többi mező érintetlen. A bemenetet nem módosítja.
export function smoothPlayerPositions(
  entries: readonly TrackingEntry[],
  fps: number,
  options: SmoothingOptions = DEFAULT_SMOOTHING,
): readonly TrackingEntry[] {
  if (!(fps > 0) || !(options.sigmaSeconds > 0)) return entries;
  const sigmaFrames = options.sigmaSeconds * fps;

  const result: TrackingEntry[] = entries.slice();
  for (const series of groupByTrack(entries)) {
    const smoothed = smoothSeries(series, sigmaFrames);
    series.rows.forEach((row, k) => {
      const original = entries[row];
      result[row] = {
        ...original,
        tx: smoothed.xs[k],
        ty: smoothed.ys[k],
        rawTx: original.tx,
        rawTy: original.ty,
      };
    });
  }
  return result;
}
