package com.ezip.app.archive;

import java.util.Locale;

/** Archive formats EZip understands. */
public enum ArchiveKind {

    ZIP, RAR, SEVEN_Z, TAR, GZIP, BZIP2, XZ, NONE;

    public static ArchiveKind of(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk")
                || n.endsWith(".docx") || n.endsWith(".pptx") || n.endsWith(".xlsx")) {
            return n.endsWith(".docx") || n.endsWith(".pptx") || n.endsWith(".xlsx") ? NONE : ZIP;
        }
        if (n.endsWith(".rar")) return RAR;
        if (n.endsWith(".7z")) return SEVEN_Z;
        if (n.endsWith(".tar")) return TAR;
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return TAR;
        if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz")) return TAR;
        if (n.endsWith(".tar.xz") || n.endsWith(".txz")) return TAR;
        if (n.endsWith(".gz")) return GZIP;
        if (n.endsWith(".bz2")) return BZIP2;
        if (n.endsWith(".xz")) return XZ;
        return NONE;
    }

    public boolean isArchive() {
        return this != NONE;
    }

    /** Human readable label for the UI. */
    public String label() {
        switch (this) {
            case ZIP: return "ZIP";
            case RAR: return "RAR";
            case SEVEN_Z: return "7Z";
            case TAR: return "TAR";
            case GZIP: return "GZIP";
            case BZIP2: return "BZIP2";
            case XZ: return "XZ";
            default: return "文件";
        }
    }
}
