package com.ezip.app.archive;

import com.ezip.app.util.FileX;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Creates ZIP / 7Z / TAR archives (TAR optionally gzip / bzip2 / xz compressed). */
public final class Compressor {

    public static final int FORMAT_ZIP = 0;
    public static final int FORMAT_7Z = 1;
    public static final int FORMAT_TAR = 2;
    public static final int FORMAT_TAR_GZ = 3;
    public static final int FORMAT_TAR_BZ2 = 4;
    public static final int FORMAT_TAR_XZ = 5;

    public static final int LEVEL_STORE = 0;
    public static final int LEVEL_NORMAL = 1;
    public static final int LEVEL_MAX = 2;

    private static final int BUF = 64 * 1024;
    private static final long REPORT_STEP = 256 * 1024;

    private Compressor() {
    }

    private static final class Item {
        final File file;
        final String name;
        final boolean dir;

        Item(File file, String name, boolean dir) {
            this.file = file;
            this.name = name;
            this.dir = dir;
        }
    }

    public static void compress(List<File> items, File outFile, int format, int level, String password,
                                ProgressSink sink, CancelToken token) throws IOException {
        if (items == null || items.isEmpty()) throw new IOException("没有要压缩的文件");
        CancelToken cancel = token == null ? new CancelToken() : token;
        // TAR has no encryption; ignore any password the user typed
        String pw = (password == null || password.isEmpty() || format >= FORMAT_TAR) ? null : password;

        List<Item> entries = new ArrayList<>();
        for (File item : items) {
            if (item == null || !item.exists()) continue;
            collect(item, item.getName(), entries, outFile);
        }
        if (entries.isEmpty()) throw new IOException("没有要压缩的文件");

        long total = 0;
        for (Item it : entries) {
            if (!it.dir) total += Math.max(0, it.file.length());
        }

        if (format >= FORMAT_TAR) {
            writeTar(entries, outFile, format, level, total, sink, cancel);
        } else if (format == FORMAT_7Z) {
            write7z(entries, outFile, level, pw, total, sink, cancel);
        } else {
            writeZip(entries, outFile, level, pw, total, sink, cancel);
        }
    }

    private static void collect(File file, String name, List<Item> out, File exclude) {
        if (file.equals(exclude)) return;
        if (file.isDirectory()) {
            out.add(new Item(file, ensureTrailingSlash(name), true));
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) {
                collect(child, name + "/" + child.getName(), out, exclude);
            }
        } else if (file.isFile()) {
            out.add(new Item(file, name, false));
        }
    }

    private static String ensureTrailingSlash(String name) {
        return name.endsWith("/") ? name : name + "/";
    }

    // ------------------------------------------------------------------ naming

    /** File extension produced by each format. */
    public static String extensionFor(int format) {
        switch (format) {
            case FORMAT_7Z: return ".7z";
            case FORMAT_TAR: return ".tar";
            case FORMAT_TAR_GZ: return ".tar.gz";
            case FORMAT_TAR_BZ2: return ".tar.bz2";
            case FORMAT_TAR_XZ: return ".tar.xz";
            default: return ".zip";
        }
    }

    /** Only ZIP and 7Z can be password protected. */
    public static boolean supportsPassword(int format) {
        return format == FORMAT_ZIP || format == FORMAT_7Z;
    }

    /** Default output name for a selection ("a.zip" for one file, "压缩包.zip" otherwise). */
    public static String suggestName(List<File> items, int format) {
        String ext = extensionFor(format);
        if (items != null && items.size() == 1 && items.get(0) != null) {
            String base = FileX.stripExtension(items.get(0).getName());
            if (base.isEmpty()) base = "压缩包";
            return base + ext;
        }
        return "压缩包" + ext;
    }

    // ------------------------------------------------------------------ zip

    private static void writeZip(final List<Item> entries, File outFile, int level, String password,
                                 final long total, final ProgressSink sink, final CancelToken token) throws IOException {
        char[] pw = password == null ? null : password.toCharArray();
        final ZipFile zip = new ZipFile(outFile, pw);
        final net.lingala.zip4j.progress.ProgressMonitor monitor = zip.getProgressMonitor();
        final boolean[] finished = {false};

        // zip4j compresses one file at a time; poll its monitor so the progress bar moves
        // and the cancel button can interrupt a single huge file.
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!finished[0]) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (token.isCancelled()) {
                        monitor.setCancelAllTasks(true);
                    }
                    if (sink != null) {
                        long done = monitor.getWorkCompleted();
                        long all = Math.max(monitor.getTotalWork(), total);
                        sink.progress(done, all, monitor.getFileName());
                    }
                }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        long done = 0;
        try {
            for (Item item : entries) {
                token.throwIfCancelled();
                ZipParameters params = zipParams(level, password != null);
                params.setFileNameInZip(item.name);
                if (item.dir) {
                    zip.addStream(new ByteArrayInputStream(new byte[0]), params);
                } else {
                    zip.addFile(item.file, params);
                    done += Math.max(0, item.file.length());
                }
                if (sink != null) sink.progress(done, total, item.name);
            }
        } catch (net.lingala.zip4j.exception.ZipException e) {
            if (token.isCancelled()) throw new OperationCancelledException();
            throw new IOException(ArchiveService.describe(e), e);
        } finally {
            finished[0] = true;
            watchdog.interrupt();
        }
    }

    private static ZipParameters zipParams(int level, boolean encrypted) {
        ZipParameters params = new ZipParameters();
        if (level == LEVEL_STORE && !encrypted) {
            params.setCompressionMethod(CompressionMethod.STORE);
        } else {
            params.setCompressionMethod(CompressionMethod.DEFLATE);
            if (level == LEVEL_MAX) {
                params.setCompressionLevel(CompressionLevel.MAXIMUM);
            } else if (level == LEVEL_STORE) {
                params.setCompressionLevel(CompressionLevel.FASTEST);
                params.setCompressionMethod(CompressionMethod.DEFLATE);
            } else {
                params.setCompressionLevel(CompressionLevel.NORMAL);
            }
        }
        if (encrypted) {
            params.setEncryptFiles(true);
            params.setEncryptionMethod(EncryptionMethod.AES);
            params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            params.setCompressionMethod(CompressionMethod.DEFLATE);
        }
        return params;
    }

    // ------------------------------------------------------------------ 7z

    private static void write7z(List<Item> entries, File outFile, int level, String password,
                                long total, ProgressSink sink, CancelToken token) throws IOException {
        char[] pw = password == null ? null : password.toCharArray();
        SevenZOutputFile out;
        try {
            out = new SevenZOutputFile(outFile, pw);
        } catch (IOException e) {
            throw new IOException(ArchiveService.describe(e), e);
        }
        long done = 0;
        byte[] buf = new byte[BUF];
        try {
            out.setContentCompression(level == LEVEL_STORE ? SevenZMethod.COPY : SevenZMethod.LZMA2);
            for (Item item : entries) {
                token.throwIfCancelled();
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(item.name);
                if (item.dir) {
                    entry.setDirectory(true);
                    entry.setHasStream(false);
                    entry.setSize(0);
                } else {
                    entry.setDirectory(false);
                    entry.setHasStream(true);
                    entry.setSize(item.file.length());
                    if (item.file.lastModified() > 0) entry.setLastModifiedDate(new Date(item.file.lastModified()));
                }
                out.putArchiveEntry(entry);
                if (!item.dir) {
                    InputStream in = new BufferedInputStream(new FileInputStream(item.file), BUF);
                    try {
                        int read;
                        long lastReport = done;
                        while ((read = in.read(buf)) > 0) {
                            token.throwIfCancelled();
                            out.write(buf, 0, read);
                            done += read;
                            if (done - lastReport >= REPORT_STEP) {
                                lastReport = done;
                                if (sink != null) sink.progress(done, total, item.name);
                            }
                        }
                    } finally {
                        in.close();
                    }
                }
                out.closeArchiveEntry();
                if (sink != null) sink.progress(done, total, item.name);
            }
            out.finish();
        } catch (IOException e) {
            if (e instanceof OperationCancelledException) throw e;
            throw new IOException(ArchiveService.describe(e), e);
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
                // already failing
            }
        }
    }

    // ------------------------------------------------------------------ tar

    private static void writeTar(List<Item> entries, File outFile, int format, int level,
                                 long total, ProgressSink sink, CancelToken token) throws IOException {
        // gzip level 1..9, bzip2 block size 1..9, xz preset 0..9
        int preset = level == LEVEL_MAX ? 9 : (level == LEVEL_STORE ? 1 : 6);

        OutputStream fileOut = null;
        TarArchiveOutputStream tar = null;
        long done = 0;
        byte[] buf = new byte[BUF];
        try {
            fileOut = new BufferedOutputStream(new FileOutputStream(outFile), BUF);
            OutputStream target = fileOut;
            switch (format) {
                case FORMAT_TAR_GZ: {
                    GzipParameters gzip = new GzipParameters();
                    gzip.setCompressionLevel(preset);
                    target = new GzipCompressorOutputStream(fileOut, gzip);
                    break;
                }
                case FORMAT_TAR_BZ2:
                    target = new BZip2CompressorOutputStream(fileOut, preset);
                    break;
                case FORMAT_TAR_XZ:
                    target = new XZCompressorOutputStream(fileOut, preset);
                    break;
                default:
                    break;
            }
            tar = new TarArchiveOutputStream(target, "UTF-8");
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            // store non-ASCII names in PAX headers so Windows / macOS / Linux all read them back
            tar.setAddPaxHeadersForNonAsciiNames(true);

            for (Item item : entries) {
                token.throwIfCancelled();
                // a trailing slash makes this a LF_DIR entry; the rest stay LF_NORMAL
                TarArchiveEntry entry = new TarArchiveEntry(item.name);
                if (item.dir) {
                    entry.setSize(0);
                    entry.setMode(0755);
                } else {
                    entry.setSize(item.file.length());
                    entry.setMode(0644);
                }
                if (item.file.lastModified() > 0) entry.setModTime(item.file.lastModified());
                tar.putArchiveEntry(entry);
                if (!item.dir) {
                    InputStream in = new BufferedInputStream(new FileInputStream(item.file), BUF);
                    try {
                        int read;
                        long lastReport = done;
                        while ((read = in.read(buf)) > 0) {
                            token.throwIfCancelled();
                            tar.write(buf, 0, read);
                            done += read;
                            if (done - lastReport >= REPORT_STEP) {
                                lastReport = done;
                                if (sink != null) sink.progress(done, total, item.name);
                            }
                        }
                    } finally {
                        in.close();
                    }
                }
                tar.closeArchiveEntry();
                if (sink != null) sink.progress(done, total, item.name);
            }
            tar.finish();
        } catch (IOException e) {
            if (e instanceof OperationCancelledException) throw e;
            throw new IOException(ArchiveService.describe(e), e);
        } finally {
            if (tar != null) {
                try {
                    tar.close();   // closes the compressor chain as well
                } catch (IOException ignored) {
                    // already failing
                }
            } else if (fileOut != null) {
                try {
                    fileOut.close();
                } catch (IOException ignored) {
                    // already failing
                }
            }
        }
    }
}
