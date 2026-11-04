export function createChatRefresh(
  run: () => Promise<void>,
  onError: (error: unknown) => void,
) {
  let active: Promise<void> | undefined;
  let queued = false;
  let disposed = false;
  return {
    refresh() {
      if (disposed) return Promise.resolve();
      queued = true;
      // Defer run until active is assigned, including when run throws synchronously.
      active ??= Promise.resolve().then(async () => {
        try {
          while (queued && !disposed) {
            queued = false;
            try {
              await run();
            } catch (error) {
              onError(error);
            }
          }
        } finally {
          active = undefined;
        }
      });
      return active;
    },
    dispose() {
      disposed = true;
      queued = false;
    },
  };
}
