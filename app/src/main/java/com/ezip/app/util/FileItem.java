package com.ezip.app.util;

import java.io.File;

/**
 * One row of the file list with everything the UI needs already computed.
 *
 * <p>Building this is the only place that touches the filesystem (stat / size / mtime),
 * and it happens once on a background thread - never inside {@code getView}.</p>
 */
public final class FileItem {

    public final File file;
    public final String name;
    public final boolean directory;
    public final long size;
    public final long time;
    /** Ready to render: "1.2 MB · 2024-05-01 10:22" or "文件夹 · 2024-05-01 10:22". */
    public final String meta;

    public FileItem(File file, String name, boolean directory, long size, long time, String meta) {
        this.file = file;
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.time = time;
        this.meta = meta;
    }

    @Override
    public String toString() {
        return name;
    }
}
