import { useLayoutEffect, type RefObject } from "react";

/** Elements inside a column that may be stretched carry this attribute. */
export const EQ_CARD_ATTR = "data-eq-card";
export const eqCard = { [EQ_CARD_ATTR]: "" } as const;

/** Page grid for two equalised columns: two columns above 1100 px, the hook's own breakpoint. */
export const twoColumnGridSx = {
  display: "grid",
  gridTemplateColumns: "minmax(0, 1fr)",
  // Columns keep their own height, so the hook can see the difference.
  alignItems: "start",
  gap: 2.5,
  "@media (min-width: 1101px)": {
    gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
  },
} as const;

const STRETCHED_ATTR = "data-eq-stretched";

interface Options {
  /** Largest bottom gap, in px, that is closed. A larger gap keeps the natural heights. */
  threshold?: number;
  /** At this viewport width and below the page is one column, and nothing is stretched. */
  breakpoint?: number;
  /** Turns the hook off without unmounting, for example while the data loads. */
  enabled?: boolean;
}

const cardsOf = (column: HTMLElement) =>
  Array.from(column.querySelectorAll<HTMLElement>(`[${EQ_CARD_ATTR}]`)).filter(
    (card) => card.offsetHeight > 0 && !card.parentElement?.closest(`[${EQ_CARD_ATTR}]`),
  );

const overflowOf = (node: HTMLElement | null) => {
  const doc = document.documentElement;
  const own = node ? node.scrollHeight - node.clientHeight : 0;
  return Math.max(own, doc.scrollHeight - doc.clientHeight);
};

const scrollParentOf = (node: HTMLElement): HTMLElement | null => {
  let current = node.parentElement;
  while (current) {
    const { overflowY } = getComputedStyle(current);
    if (overflowY === "auto" || overflowY === "scroll") return current;
    current = current.parentElement;
  }
  return null;
};

/**
 * Two-column pages: when the two column bottoms differ by only a few pixels, the shorter column's
 * last card (or the cards of its last row) gets a min-height so both bottoms line up exactly. The
 * content stays at the top of the card. A larger gap keeps the natural heights, so no card turns into
 * a big empty box. One-column layouts are left alone, and a stretch that would make the page scroll
 * is undone.
 *
 * Mark every card that may be stretched with {@link eqCard} (`data-eq-card`). The columns must not be
 * stretched by their grid (use `alignItems: "start"`), otherwise both always report the same height.
 */
export const useEqualColumns = (
  left: RefObject<HTMLElement | null>,
  right: RefObject<HTMLElement | null>,
  { threshold = 72, breakpoint = 1100, enabled = true }: Options = {},
) => {
  useLayoutEffect(() => {
    const leftColumn = left.current;
    const rightColumn = right.current;
    if (!enabled || !leftColumn || !rightColumn) return;

    let frame = 0;

    const clear = () => {
      for (const column of [leftColumn, rightColumn]) {
        column.querySelectorAll<HTMLElement>(`[${STRETCHED_ATTR}]`).forEach((card) => {
          card.style.minHeight = "";
          card.removeAttribute(STRETCHED_ATTR);
        });
      }
    };

    const measure = () => {
      frame = 0;
      clear();
      if (window.innerWidth <= breakpoint) return;
      // Stacked columns (one-column grid) share no bottom line.
      const a = leftColumn.getBoundingClientRect();
      const b = rightColumn.getBoundingClientRect();
      if (Math.abs(a.top - b.top) > 1 || a.right > b.left + 1) return;

      const info = [leftColumn, rightColumn].map((column) => {
        const cards = cardsOf(column);
        if (cards.length === 0) return null;
        const bottoms = cards.map((card) => card.getBoundingClientRect().bottom);
        const bottom = Math.max(...bottoms);
        return { bottom, last: cards.filter((_, index) => Math.abs(bottoms[index] - bottom) < 1.5) };
      });
      const [first, second] = info;
      if (!first || !second) return;
      const delta = second.bottom - first.bottom;
      const gap = Math.abs(delta);
      if (gap < 0.5 || gap > threshold) return;

      const shorter = delta > 0 ? first : second;
      const scroller = scrollParentOf(leftColumn);
      const before = overflowOf(scroller);
      const heights = shorter.last.map((card) => card.getBoundingClientRect().height);
      shorter.last.forEach((card, index) => {
        card.style.minHeight = `${heights[index] + gap}px`;
        card.setAttribute(STRETCHED_ATTR, "");
      });
      if (overflowOf(scroller) > before + 0.5) clear();
    };

    const schedule = () => {
      if (!frame) frame = requestAnimationFrame(measure);
    };

    measure();
    // Clearing and re-applying the same min-height in one pass leaves the size unchanged, so the
    // observers do not loop.
    const resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(leftColumn);
    resizeObserver.observe(rightColumn);
    const mutationObserver = new MutationObserver(schedule);
    mutationObserver.observe(leftColumn, { childList: true, subtree: true, characterData: true });
    mutationObserver.observe(rightColumn, { childList: true, subtree: true, characterData: true });
    window.addEventListener("resize", schedule);
    void document.fonts?.ready.then(schedule);

    return () => {
      if (frame) cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      mutationObserver.disconnect();
      window.removeEventListener("resize", schedule);
      clear();
    };
  }, [left, right, threshold, breakpoint, enabled]);
};
