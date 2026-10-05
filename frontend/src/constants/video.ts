// A tracking-adat dobozai ebben a térben vannak: a backend a nyers felbontásról erre skálázza őket
// (HLS_OUTPUT_WIDTH/HEIGHT az ML worker config.py-jában és az encoder/main.py-ban; a három értéknek egyeznie kell).
export const VIDEO_VIEW = { width: 1280, height: 720 } as const;
