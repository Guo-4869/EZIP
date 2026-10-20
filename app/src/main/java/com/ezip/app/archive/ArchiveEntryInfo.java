package com.ezip.app.archive;

/** One entry inside an archive, used by the archive content viewer. */
public final class ArchiveEntryInfo {

    public final String name;
    public final long size;
    public final boolean directory;
    public final long time;

    public ArchiveEntryInfo(String name, long size, boolean directory, long time) {
        this.name = name;
        this.size = size;
        this.directory = directory;
        this.time = time;
    }

    @Override
    public String toString() {
        return name;
    }
}
