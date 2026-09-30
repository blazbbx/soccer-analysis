export const PAGE_SIZE = 10;

export function matchesQuery(query: string, fields: Array<string | null | undefined>): boolean {
  const needle = query.trim().toLocaleLowerCase();
  if (!needle) return true;
  return fields.some((field) => (field ?? "").toLocaleLowerCase().includes(needle));
}

export function pageOf<T>(items: T[], page: number) {
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(Math.max(1, page), totalPages);
  const start = (safePage - 1) * PAGE_SIZE;
  return { totalPages, safePage, visible: items.slice(start, start + PAGE_SIZE) };
}

export function monogram(name?: string | null, shortName?: string | null): string {
  const short = shortName?.trim();
  if (short) return short;
  return (name ?? "")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}
