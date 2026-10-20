package com.ezip.app.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists a directory once, on a background thread, and returns rows that are cheap to draw.
 *
 * <p>Why this exists: doing {@code listFiles()} + {@code length()} / {@code lastModified()}
 * on the UI thread costs one {@code stat} syscall per entry per access, and a comparator
 * multiplies that by O(n log n). A folder with a few thousand files froze the list for
 * seconds. Everything here is pure {@code java.io}, so it is exercised by the JVM tests.</p>
 */
public final class DirLoader {

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;

    private DirLoader() {
    }

    /**
     * @throws IOException when the directory cannot be listed (no permission / not a directory)
     */
    public static List<FileItem> load(File dir, boolean showHidden, int sortMode,
                                      boolean showParentPath, File storageRoot) throws IOException {
        if (dir == null) throw new IOException("目录为空");
        File[] all = dir.listFiles();
        if (all == null) throw new IOException("无法读取目录: " + dir.getAbsolutePath());

        List<FileItem> items = new ArrayList<>(all.length);
        for (File child : all) {
            if (child == null) continue;
            String name = child.getName();
            if (!showHidden && name.startsWith(".")) continue;
            items.add(item(child, showParentPath, storageRoot));
        }
        sort(items, sortMode);
        return items;
    }

    /** Converts a single file into a row (used for search results too). */
    public static FileItem item(File file, boolean showParentPath, File storageRoot) {
        boolean directory = file.isDirectory();
        long size = directory ? 0L : file.length();
        long time = file.lastModified();
        String meta;
        if (showParentPath) {
            String where = FileX.folderLabel(file.getParentFile(), storageRoot);
            meta = directory ? "文件夹  ·  " + where : FileX.formatSize(size) + "  ·  " + where;
        } else if (directory) {
            String stamp = FileX.formatTime(time);
            meta = stamp.isEmpty() ? "文件夹" : "文件夹  ·  " + stamp;
        } else {
            String stamp = FileX.formatTime(time);
            meta = stamp.isEmpty() ? FileX.formatSize(size) : FileX.formatSize(size) + "  ·  " + stamp;
        }
        return new FileItem(file, file.getName(), directory, size, time, meta);
    }

    /** Directories first, then the chosen key (no filesystem access: uses cached fields). */
    public static void sort(List<FileItem> items, final int sortMode) {
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                if (a.directory != b.directory) return a.directory ? -1 : 1;
                if (sortMode == SORT_SIZE && !a.directory && a.size != b.size) {
                    return a.size > b.size ? -1 : 1;
                }
                if (sortMode == SORT_DATE && a.time != b.time) {
                    return a.time > b.time ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }
}
