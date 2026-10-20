package com.ezip.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal file provider (no AndroidX dependency) so files inside archives can be
 * handed to other apps as content:// URIs without tripping FileUriExposedException.
 */
public class EzFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.ezip.app.files";

    public static Uri uriFor(File file) {
        String absolute = file.getAbsolutePath();
        StringBuilder sb = new StringBuilder("content://").append(AUTHORITY);
        for (String segment : absolute.split("/")) {
            if (segment.isEmpty()) continue;
            sb.append('/').append(Uri.encode(segment));
        }
        return Uri.parse(sb.toString());
    }

    public static File fileFor(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return null;
        return new File(path);
    }

    public static String mimeOf(String fileName) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
        String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return mime == null ? "application/octet-stream" : mime;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Only files under shared storage or our own sandbox may be exported. */
    private boolean isAllowed(File file) {
        if (file == null) return false;
        try {
            String path = file.getCanonicalPath();
            List<File> roots = new ArrayList<>();
            roots.add(Environment.getExternalStorageDirectory());
            Context context = getContext();
            if (context != null) {
                roots.add(context.getFilesDir());
                roots.add(context.getCacheDir());
                File external = context.getExternalFilesDir(null);
                if (external != null) roots.add(external);
            }
            for (File root : roots) {
                if (root == null) continue;
                String base = root.getCanonicalPath();
                if (path.equals(base) || path.startsWith(base + File.separator)) return true;
            }
        } catch (Exception ignored) {
            // fall through to "denied"
        }
        return false;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileFor(uri);
        if (file == null || !file.isFile() || !file.canRead() || !isAllowed(file)) {
            throw new FileNotFoundException("not a readable file: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        if (file == null || !file.isFile() || !isAllowed(file)) return null;
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = fileFor(uri);
        return (file == null || !isAllowed(file)) ? "application/octet-stream" : mimeOf(file.getName());
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only provider");
    }

    @SuppressWarnings("unused")
    private static void closeQuietly(ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
