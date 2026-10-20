package com.ezip.app.archive;

/** Receives byte based progress from the archive engine. */
public interface ProgressSink {

    /**
     * @param done  bytes processed so far
     * @param total total bytes, or 0 when unknown
     * @param label currently processed entry (may be null)
     */
    void progress(long done, long total, String label);
}
