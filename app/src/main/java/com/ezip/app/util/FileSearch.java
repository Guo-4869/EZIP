package com.ezip.app.util;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive file name search (breadth first, so shallow matches come first).
 * Android free on purpose: the JVM test harness exercises the same code.
 */
public final class FileSearch {

    public interface Callback {
        /** Polled between directories; return true to stop early. */
        boolean isCancelled();

        /** Called from the worker thread with the results collected so far. */
        void onProgress(List<File> results, int scannedDirs);
    }

    private static final int MAX_DEPTH = 12;

    private FileSearch() {
    }

    /**
     * @param root          where to start (searched recursively)
     * @param query         case insensitive substring of the file name
     * @param includeHidden include dot files / dot folders
     * @param maxResults    stop once this many matches were collected (<=0 means unlimited)
     * @param maxDirs       safety cap on how many directories are scanned (<=0 means unlimited)
     */
    public static List<File> search(File root, String query, boolean includeHidden,
                                    int maxResults, int maxDirs, Callback callback) {
        List<File> results = new ArrayList<>();
        if (root == null || query == null) return results;
        String needle = query.trim().toLowerCase(Locale.US);
        if (needle.isEmpty()) return results;

        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root, 0));
        int scannedDirs = 0;
        int matchedSinceReport = 0;

        while (!queue.isEmpty()) {
            if (callback != null && callback.isCancelled()) break;
            if (maxResults > 0 && results.size() >= maxResults) break;
            if (maxDirs > 0 && scannedDirs >= maxDirs) break;

            Node node = queue.poll();
            scannedDirs++;

            File[] children;
            try {
                children = node.dir.listFiles();
            } catch (SecurityException e) {
                children = null;
            }
            if (children == null) continue;

            for (File child : children) {
                String name = child.getName();
                if (!includeHidden && name.startsWith(".")) continue;
                if (name.toLowerCase(Locale.US).contains(needle)) {
                    results.add(child);
                    matchedSinceReport++;
                }
                if (node.depth + 1 <= MAX_DEPTH && child.isDirectory()) {
                    queue.add(new Node(child, node.depth + 1));
                }
                if (maxResults > 0 && results.size() >= maxResults) break;
            }

            if (callback != null && (matchedSinceReport >= 20 || scannedDirs % 40 == 0)) {
                matchedSinceReport = 0;
                callback.onProgress(new ArrayList<>(results), scannedDirs);
            }
        }

        if (callback != null) callback.onProgress(new ArrayList<>(results), scannedDirs);
        return results;
    }

    private static final class Node {
        final File dir;
        final int depth;

        Node(File dir, int depth) {
            this.dir = dir;
            this.depth = depth;
        }
    }
}
