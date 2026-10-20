package com.ezip.app.archive;

/** Cooperative cancellation flag shared between the UI thread and a worker thread. */
public final class CancelToken {

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() throws OperationCancelledException {
        if (cancelled) {
            throw new OperationCancelledException();
        }
    }
}
