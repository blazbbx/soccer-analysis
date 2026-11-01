/** A covered area on one page, as fractions of the page, so it does not depend on the size on the screen. */
export interface RedactionRect {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export type RedactionErrorKey = "too-many-pages" | "password" | "unreadable";

export class RedactionError extends Error {
  readonly key: RedactionErrorKey;

  constructor(key: RedactionErrorKey) {
    super(key);
    this.key = key;
  }
}

const MAX_PAGES = 6;
/** Legible on the verifier's screen, and small enough for the upload limit. */
const PDF_TARGET_WIDTH = 1600;
const IMAGE_MAX_SIDE = 2400;
const PAGE_GAP = 16;
const JPEG_QUALITY = 0.85;

const newCanvas = (width: number, height: number) => {
  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(width);
  canvas.height = Math.ceil(height);
  return canvas;
};

const context2d = (canvas: HTMLCanvasElement) => {
  const context = canvas.getContext("2d");
  if (!context) throw new RedactionError("unreadable");
  return context;
};

const renderImage = async (file: File) => {
  const bitmap = await createImageBitmap(file).catch(() => {
    throw new RedactionError("unreadable");
  });
  const scale = Math.min(1, IMAGE_MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = newCanvas(bitmap.width * scale, bitmap.height * scale);
  context2d(canvas).drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return [canvas];
};

const renderPdf = async (file: File) => {
  // Loaded only here: most users never need it. The legacy build also runs on older phones.
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  const { default: PdfWorker } = await import("pdfjs-dist/legacy/build/pdf.worker.min.mjs?worker");
  const worker = new PdfWorker();
  pdfjs.GlobalWorkerOptions.workerPort = worker;
  const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) });
  try {
    const document = await task.promise;
    if (document.numPages > MAX_PAGES) throw new RedactionError("too-many-pages");
    const pages: HTMLCanvasElement[] = [];
    for (let number = 1; number <= document.numPages; number += 1) {
      const page = await document.getPage(number);
      const base = page.getViewport({ scale: 1 });
      const viewport = page.getViewport({ scale: PDF_TARGET_WIDTH / base.width });
      const canvas = newCanvas(viewport.width, viewport.height);
      await page.render({ canvas, canvasContext: context2d(canvas), viewport }).promise;
      pages.push(canvas);
    }
    return pages;
  } catch (err) {
    if (err instanceof RedactionError) throw err;
    throw new RedactionError((err as { name?: string }).name === "PasswordException" ? "password" : "unreadable");
  } finally {
    await task.destroy();
    worker.terminate();
  }
};

/** Everything happens in the browser: the file picked here is not sent anywhere. */
export const renderForRedaction = (file: File): Promise<HTMLCanvasElement[]> =>
  file.type === "application/pdf" ? renderPdf(file) : renderImage(file);

/**
 * Draws the pages and the covers into one image. Only pixels stay: no text layer, no metadata and nothing under the
 * black areas, so a cover cannot be lifted off later. Whole pixels are rounded outwards, so no edge shows through.
 */
export const flattenRedaction = async (pages: HTMLCanvasElement[], rects: RedactionRect[]): Promise<File> => {
  const width = Math.max(...pages.map((page) => page.width));
  const height = pages.reduce((sum, page) => sum + page.height, 0) + PAGE_GAP * (pages.length - 1);
  const output = newCanvas(width, height);
  const context = context2d(output);
  context.fillStyle = "#fff";
  context.fillRect(0, 0, width, height);
  let top = 0;
  pages.forEach((page, index) => {
    context.drawImage(page, 0, top);
    context.fillStyle = "#000";
    rects.filter((rect) => rect.page === index).forEach((rect) => {
      const left = Math.floor(rect.x * page.width);
      const upper = Math.floor(rect.y * page.height);
      const right = Math.ceil((rect.x + rect.width) * page.width);
      const lower = Math.ceil((rect.y + rect.height) * page.height);
      context.fillRect(left, top + upper, right - left, lower - upper);
    });
    top += page.height + PAGE_GAP;
  });
  const blob = await new Promise<Blob | null>((resolve) => output.toBlob(resolve, "image/jpeg", JPEG_QUALITY));
  output.width = 0;
  if (!blob) throw new RedactionError("unreadable");
  return new File([blob], "sportorvosi-kitakart.jpg", { type: "image/jpeg" });
};
