const inflight = new Map<string, Promise<unknown>>();

export function acceptInviteOnce<T>(token: string, work: () => Promise<T>): Promise<T> {
  const existing = inflight.get(token);
  if (existing) {
    return existing as Promise<T>;
  }
  const promise = work().catch((error: unknown) => {
    inflight.delete(token);
    throw error;
  });
  inflight.set(token, promise);
  return promise;
}
