// A lassú fogyasztók (időkijelző, csúszka) így csak akkor renderelődnek újra, ha az érték tényleg
// átlép egy lépcsőt, nem minden képkockánál.
export function quantizeTime(seconds: number, resolution: number): number {
  return resolution > 0 ? Math.floor(seconds / resolution) * resolution : seconds;
}
