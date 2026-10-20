package com.ezip.app.ui;

import com.ezip.app.R;
import com.ezip.app.archive.ArchiveKind;
import com.ezip.app.util.FileX;

import java.util.Locale;

/** Maps a file name to a badge colour + short label, so the list stays readable at a glance. */
public enum Kind {

    FOLDER(R.color.primary),
    IMAGE(R.color.badge_image),
    VIDEO(R.color.badge_video),
    AUDIO(R.color.badge_audio),
    PDF(R.color.badge_pdf),
    DOC(R.color.badge_doc),
    PPT(R.color.badge_ppt),
    XLS(R.color.badge_xls),
    ARCHIVE(R.color.badge_archive),
    TEXT(R.color.badge_text),
    APK(R.color.badge_apk),
    CODE(R.color.badge_code),
    OTHER(R.color.badge_generic);

    public final int colorRes;

    Kind(int colorRes) {
        this.colorRes = colorRes;
    }

    public static Kind of(String fileName) {
        String ext = FileX.extension(fileName);
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp":
            case "heic": case "heif": case "tif": case "tiff": case "svg": case "ico":
                return IMAGE;
            case "mp4": case "mkv": case "avi": case "mov": case "3gp": case "webm":
            case "flv": case "ts": case "m4v": case "wmv": case "rmvb": case "mpeg": case "mpg":
                return VIDEO;
            case "mp3": case "wav": case "flac": case "aac": case "ogg": case "m4a":
            case "amr": case "wma": case "ape": case "opus":
                return AUDIO;
            case "pdf":
                return PDF;
            case "doc": case "docx": case "rtf": case "odt": case "wps":
                return DOC;
            case "ppt": case "pptx": case "odp": case "dps":
                return PPT;
            case "xls": case "xlsx": case "csv": case "ods": case "et":
                return XLS;
            case "apk": case "xapk": case "aab":
                return APK;
            default:
                break;
        }
        ArchiveKind archive = ArchiveKind.of(fileName);
        if (archive.isArchive()) return ARCHIVE;
        if (FileX.isTextName(fileName)) {
            switch (ext) {
                case "java": case "kt": case "c": case "h": case "cpp": case "hpp": case "cs":
                case "py": case "js": case "ts": case "sh": case "bat": case "go": case "rs":
                case "php": case "rb": case "swift": case "lua": case "sql":
                    return CODE;
                default:
                    return TEXT;
            }
        }
        return OTHER;
    }

    /** Short badge text: "PDF", "DOCX", "MP4"... empty for folders. */
    public String label(String fileName) {
        if (this == FOLDER) return "";
        String ext = FileX.extension(fileName);
        if (ext.isEmpty()) return "?";
        return ext.length() <= 4 ? ext.toUpperCase(Locale.US) : ext.substring(0, 4).toUpperCase(Locale.US);
    }
}
