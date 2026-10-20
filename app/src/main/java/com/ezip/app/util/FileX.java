package com.ezip.app.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small, Android independent file helpers (also used by the desktop engine test). */
public final class FileX {

    public static final Charset GBK = Charset.forName("GBK");

    private FileX() {
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    /** Reused per thread: creating a SimpleDateFormat per row was measurable on big folders. */
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        }
    };

    public static String formatTime(long millis) {
        if (millis <= 0) return "";
        return TIME_FORMAT.get().format(new Date(millis));
    }

    /** "内部存储/Download" style label; storageRoot lets the JVM tests control the prefix. */
    public static String folderLabel(File dir, File storageRoot) {
        if (dir == null) return "";
        String path = dir.getAbsolutePath();
        if (storageRoot == null) return path;
        String root = storageRoot.getAbsolutePath();
        if (path.equals(root)) return "内部存储";
        if (path.startsWith(root + "/")) return "内部存储" + path.substring(root.length());
        return path;
    }

    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    /** "photo.tar.gz" -> "photo.tar", "movie.mp4" -> "movie", ".profile" -> ".profile" */
    public static String stripExtension(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.US);
        for (String multi : new String[]{".tar.gz", ".tar.bz2", ".tar.xz"}) {
            if (lower.endsWith(multi) && name.length() > multi.length()) {
                return name.substring(0, name.length() - multi.length());
            }
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name;
        return name.substring(0, dot);
    }

    public static boolean isTextName(String name) {
        String e = extension(name);
        switch (e) {
            case "txt": case "md": case "markdown": case "log": case "json": case "xml":
            case "csv": case "yml": case "yaml": case "ini": case "conf": case "cfg":
            case "html": case "htm": case "css": case "js": case "ts": case "java":
            case "kt": case "c": case "h": case "cpp": case "hpp": case "cs": case "py":
            case "sh": case "bat": case "gradle": case "properties": case "srt": case "vtt":
            case "sql": case "php": case "rb": case "go": case "rs": case "swift": case "lua":
            case "tex": case "gitignore": case "pro": case "rc": case "toml":
                return true;
            default:
                return false;
        }
    }

    public static boolean deleteRecursive(File target) {
        if (target == null || !target.exists()) return true;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return target.delete();
    }

    /** Returns a file in dir whose name is unique ("report.txt" -> "report (1).txt"). */
    public static File uniqueFile(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;
        String base = stripExtension(name);
        String ext = name.substring(base.length());
        for (int i = 1; i < 10000; i++) {
            candidate = new File(dir, base + " (" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    public static File uniqueDir(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;
        for (int i = 1; i < 10000; i++) {
            candidate = new File(dir, name + " (" + i + ")");
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, name + "_" + System.currentTimeMillis());
    }

    public static boolean ensureDir(File dir) throws IOException {
        if (dir.isDirectory()) return true;
        if (dir.exists()) throw new IOException("存在同名文件: " + dir.getName());
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }
        return true;
    }

    /**
     * Resolves an archive entry name inside {@code destDir}, rejecting path traversal
     * ("../", "/etc", "C:\...") so a malicious archive cannot escape the target folder.
     */
    public static File safeResolve(File destDir, String entryName) throws IOException {
        if (entryName == null) throw new IOException("无效的条目名称");
        String name = entryName.replace('\\', '/');
        while (name.startsWith("/")) name = name.substring(1);
        if (name.length() > 1 && name.charAt(1) == ':') name = name.substring(2);
        File out = new File(destDir, name);
        String base = destDir.getCanonicalPath();
        String path = out.getCanonicalPath();
        if (!path.equals(base) && !path.startsWith(base + File.separator)) {
            throw new IOException("压缩包包含非法路径: " + entryName);
        }
        return out;
    }

    /** Reads a text file, auto detecting UTF-8 vs GBK, capped at maxBytes. */
    public static String readText(File file, int maxBytes) throws IOException {
        byte[] bytes = readBytes(file, maxBytes);
        return decode(bytes);
    }

    public static byte[] readBytes(File file, int maxBytes) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buf)) > 0) {
                int keep = Math.min(read, maxBytes - total);
                if (keep <= 0) break;
                out.write(buf, 0, keep);
                total += keep;
            }
            return out.toByteArray();
        }
    }

    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, GBK);
        }
    }
}
