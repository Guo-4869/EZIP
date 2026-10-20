package com.ezip.app.archive;

import com.ezip.app.util.FileX;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import net.lingala.zip4j.exception.ZipException;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads (extracts / lists) every archive format EZip supports.
 * Pure Java on purpose: no android.* imports, so the same code can be exercised
 * by the desktop test harness in tools/.
 */
public final class ArchiveService {

    private static final int BUF = 64 * 1024;
    private static final long REPORT_STEP = 256 * 1024;
    /** Check free space while writing, so a huge / bomb archive cannot fill the device. */
    private static final long DISK_CHECK_STEP = 64L * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 32L * 1024 * 1024;

    private ArchiveService() {
    }

    // ------------------------------------------------------------------ public

    public static void extract(File archive, File destDir, String password,
                               ProgressSink sink, CancelToken token) throws IOException {
        if (archive == null || !archive.isFile()) throw new IOException("压缩包不存在");
        token = token == null ? new CancelToken() : token;
        FileX.ensureDir(destDir);
        ArchiveKind kind = ArchiveKind.of(archive.getName());
        switch (kind) {
            case ZIP:
                extractZip(archive, destDir, password, sink, token);
                break;
            case RAR:
                extractRar(archive, destDir, password, sink, token);
                break;
            case SEVEN_Z:
                extract7z(archive, destDir, password, sink, token);
                break;
            case TAR:
                extractTar(archive, destDir, sink, token);
                break;
            case GZIP:
            case BZIP2:
            case XZ:
                extractSingleStream(archive, destDir, kind, sink, token);
                break;
            default:
                throw new IOException("不支持的文件格式: " + archive.getName());
        }
    }

    /**
     * Best effort check whether entries are password protected. ZIP and RAR expose this
     * in their headers without decrypting anything; 7Z is left to the caller, which gets
     * a password error the first time it tries to read an entry.
     */
    public static boolean isEncrypted(File archive) {
        if (archive == null || !archive.isFile()) return false;
        try {
            switch (ArchiveKind.of(archive.getName())) {
                case ZIP: {
                    net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(archive, (char[]) null);
                    return zf.isEncrypted();
                }
                case RAR: {
                    Archive rar = new Archive(archive, (String) null);
                    try {
                        return rar.isEncrypted() || rar.isPasswordProtected();
                    } finally {
                        closeQuietly(rar);
                    }
                }
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the exception means "needs a (correct) password". */
    public static boolean isPasswordError(Exception e) {
        if (e == null) return false;
        String msg = describe(e);
        String raw = String.valueOf(e.getMessage());
        String lower = raw.toLowerCase();
        if (msg.contains("密码")) return true;
        return lower.contains("password") || lower.contains("decrypt") || lower.contains("encrypted");
    }

    public static List<ArchiveEntryInfo> list(File archive, String password) throws IOException {
        ArchiveKind kind = ArchiveKind.of(archive.getName());
        List<ArchiveEntryInfo> out = new ArrayList<>();
        switch (kind) {
            case ZIP:
                return listZip(archive, password);
            case RAR: {
                Archive rar = openRar(archive, password);
                try {
                    for (FileHeader h : rar.getFileHeaders()) {
                        out.add(new ArchiveEntryInfo(h.getFileName(), h.getFullUnpackSize(),
                                h.isDirectory(), h.getMTime() == null ? 0 : h.getMTime().getTime()));
                    }
                } catch (Exception e) {
                    throw new IOException(describe(e), e);
                } finally {
                    closeQuietly(rar);
                }
                return out;
            }
            case SEVEN_Z: {
                SevenZFile z = open7z(archive, password);
                try {
                    for (SevenZArchiveEntry e : z.getEntries()) {
                        long time = e.getHasLastModifiedDate() ? e.getLastModifiedDate().getTime() : 0;
                        out.add(new ArchiveEntryInfo(e.getName(), e.getSize(), e.isDirectory(), time));
                    }
                } finally {
                    closeQuietly(z);
                }
                return out;
            }
            case TAR: {
                TarArchiveInputStream tis = openTar(archive);
                try {
                    TarArchiveEntry e;
                    while ((e = tis.getNextEntry()) != null) {
                        out.add(new ArchiveEntryInfo(e.getName(), e.getSize(), e.isDirectory(),
                                e.getModTime() == null ? 0 : e.getModTime().getTime()));
                    }
                } finally {
                    closeQuietly(tis);
                }
                return out;
            }
            case GZIP:
            case BZIP2:
            case XZ: {
                String name = FileX.stripExtension(archive.getName());
                out.add(new ArchiveEntryInfo(name, 0, false, archive.lastModified()));
                return out;
            }
            default:
                throw new IOException("不支持的文件格式: " + archive.getName());
        }
    }

    /** Extracts one entry of an archive into outFile (used by the content viewer). */
    public static void extractEntry(File archive, String entryName, String password, File outFile) throws IOException {
        ArchiveKind kind = ArchiveKind.of(archive.getName());
        CancelToken token = new CancelToken();
        if (kind == ArchiveKind.ZIP) {
            net.lingala.zip4j.ZipFile zf = openZip(archive, password);
            List<net.lingala.zip4j.model.FileHeader> headers = zf.getFileHeaders();
            for (net.lingala.zip4j.model.FileHeader h : headers) {
                if (!entryName.equals(h.getFileName())) continue;
                File parent = outFile.getParentFile();
                if (parent != null) FileX.ensureDir(parent);
                InputStream in = zf.getInputStream(h);
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    copy(in, os, token, 0, 0, null, null, outFile.getParentFile());
                } finally {
                    closeQuietly(in);
                }
                return;
            }
            throw new IOException("压缩包中没有该文件");
        } else if (kind == ArchiveKind.RAR) {
            Archive rar = openRar(archive, password);
            try {
                FileHeader target = null;
                for (FileHeader h : rar.getFileHeaders()) {
                    if (entryName.equals(h.getFileName())) target = h;
                    if (target != null) break;
                }
                if (target == null) throw new IOException("压缩包中没有该文件");
                File parent = outFile.getParentFile();
                if (parent != null) FileX.ensureDir(parent);
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    rar.extractFile(target, os);
                } catch (Exception e) {
                    throw new IOException(describe(e), e);
                }
            } finally {
                closeQuietly(rar);
            }
            return;
        } else if (kind == ArchiveKind.SEVEN_Z) {
            SevenZFile z = open7z(archive, password);
            try {
                SevenZArchiveEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    if (!entryName.equals(e.getName())) continue;
                    File parent = outFile.getParentFile();
                    if (parent != null) FileX.ensureDir(parent);
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buf = new byte[BUF];
                        int read;
                        while ((read = z.read(buf, 0, buf.length)) > 0) os.write(buf, 0, read);
                    }
                    return;
                }
            } finally {
                closeQuietly(z);
            }
            throw new IOException("压缩包中没有该文件");
        }
        // fall back to a full extraction into a temp folder and pick the entry
        File tmp = new File(outFile.getParentFile(), "tmp_" + System.currentTimeMillis());
        try {
            extract(archive, tmp, password, null, token);
            File found = new File(tmp, entryName);
            if (!found.isFile()) throw new IOException("压缩包中没有该文件");
            File parent = outFile.getParentFile();
            if (parent != null) FileX.ensureDir(parent);
            if (!found.renameTo(outFile)) {
                try (InputStream in = new FileInputStream(found);
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    copy(in, os, token, 0, 0, null, null, outFile.getParentFile());
                }
            }
        } finally {
            FileX.deleteRecursive(tmp);
        }
    }

    // ------------------------------------------------------------------ zip

    private static net.lingala.zip4j.ZipFile openZip(File archive, String password) throws IOException {
        char[] pw = (password == null || password.isEmpty()) ? null : password.toCharArray();
        try {
            // zip4j decodes entries without the UTF-8 flag as Cp437, which turns Chinese
            // names into boxes. Try explicit UTF-8 first (our own archives, most tools),
            // then GBK (legacy Windows archives), and only then keep the Cp437 default.
            net.lingala.zip4j.ZipFile utf8 = new net.lingala.zip4j.ZipFile(archive, pw);
            utf8.setCharset(StandardCharsets.UTF_8);
            if (!hasBrokenNames(utf8.getFileHeaders())) return utf8;

            net.lingala.zip4j.ZipFile gbk = new net.lingala.zip4j.ZipFile(archive, pw);
            gbk.setCharset(FileX.GBK);
            if (!hasBrokenNames(gbk.getFileHeaders())) return gbk;

            net.lingala.zip4j.ZipFile plain = new net.lingala.zip4j.ZipFile(archive, pw);
            plain.getFileHeaders();
            return plain;
        } catch (ZipException e) {
            throw new IOException(describe(e), e);
        }
    }

    private static boolean hasBrokenNames(List<net.lingala.zip4j.model.FileHeader> headers) {
        for (net.lingala.zip4j.model.FileHeader h : headers) {
            if (looksLikeMojibake(h.getFileName())) return true;
        }
        return false;
    }

    /** True when the charset just tried could not represent the raw name bytes. */
    private static boolean looksLikeMojibake(String name) {
        if (name == null || name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\uFFFD') return true;                    // undecodable byte
            if (c >= '\u0080' && c <= '\u009F') return true;   // C1 controls
        }
        return false;
    }

    private static List<ArchiveEntryInfo> listZip(File archive, String password) throws IOException {
        net.lingala.zip4j.ZipFile zf = openZip(archive, password);
        List<ArchiveEntryInfo> out = new ArrayList<>();
        for (net.lingala.zip4j.model.FileHeader h : zf.getFileHeaders()) {
            // getLastModifiedTime() is a packed DOS time, getLastModifiedTimeEpoch() is millis
            out.add(new ArchiveEntryInfo(h.getFileName(), h.getUncompressedSize(),
                    h.isDirectory(), h.getLastModifiedTimeEpoch()));
        }
        return out;
    }

    private static void extractZip(File archive, File destDir, String password,
                                   ProgressSink sink, CancelToken token) throws IOException {
        net.lingala.zip4j.ZipFile zf = openZip(archive, password);
        List<net.lingala.zip4j.model.FileHeader> headers = zf.getFileHeaders();
        long total = 0;
        for (net.lingala.zip4j.model.FileHeader h : headers) total += Math.max(0, h.getUncompressedSize());
        long done = 0;
        for (net.lingala.zip4j.model.FileHeader h : headers) {
            token.throwIfCancelled();
            String name = h.getFileName();
            File out = FileX.safeResolve(destDir, name);
            if (h.isDirectory()) {
                FileX.ensureDir(out);
                continue;
            }
            File parent = out.getParentFile();
            if (parent != null) FileX.ensureDir(parent);
            InputStream in = null;
            OutputStream os = null;
            try {
                in = zf.getInputStream(h);
                os = new BufferedOutputStream(new FileOutputStream(out), BUF);
                long copied = copy(in, os, token, total, done, name, sink, destDir);
                done += copied;
            } catch (ZipException e) {
                throw new IOException(describe(e), e);
            } finally {
                closeQuietly(in);
                closeQuietly(os);
            }
            if (h.getLastModifiedTimeEpoch() > 0) out.setLastModified(h.getLastModifiedTimeEpoch());
            if (sink != null) sink.progress(done, total, name);
        }
    }

    // ------------------------------------------------------------------ rar

    private static Archive openRar(File archive, String password) throws IOException {
        try {
            return new Archive(archive, (password == null || password.isEmpty()) ? null : password);
        } catch (Exception e) {
            throw new IOException(describe(e), e);
        }
    }

    private static void extractRar(File archive, File destDir, String password,
                                   ProgressSink sink, CancelToken token) throws IOException {
        Archive rar = openRar(archive, password);
        try {
            List<FileHeader> headers = rar.getFileHeaders();
            long total = 0;
            for (FileHeader h : headers) total += Math.max(0, h.getFullUnpackSize());
            long done = 0;
            for (FileHeader h : headers) {
                token.throwIfCancelled();
                String name = h.getFileName();
                File out = FileX.safeResolve(destDir, name);
                if (h.isDirectory()) {
                    FileX.ensureDir(out);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) FileX.ensureDir(parent);
                CountingOutputStream os = new CountingOutputStream(
                        new BufferedOutputStream(new FileOutputStream(out), BUF), token, sink, total, done, name, destDir);
                try {
                    rar.extractFile(h, os);
                } catch (Exception e) {
                    throw new IOException(describe(e), e);
                } finally {
                    closeQuietly(os);
                }
                done += Math.max(0, h.getFullUnpackSize());
                if (h.getMTime() != null && h.getMTime().getTime() > 0) out.setLastModified(h.getMTime().getTime());
                if (sink != null) sink.progress(done, total, name);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(describe(e), e);
        } finally {
            closeQuietly(rar);
        }
    }

    // ------------------------------------------------------------------ 7z

    private static SevenZFile open7z(File archive, String password) throws IOException {
        try {
            char[] pw = (password == null || password.isEmpty()) ? null : password.toCharArray();
            return new SevenZFile(archive, pw);
        } catch (IOException e) {
            throw new IOException(describe(e), e);
        }
    }

    private static void extract7z(File archive, File destDir, String password,
                                  ProgressSink sink, CancelToken token) throws IOException {
        SevenZFile z = open7z(archive, password);
        try {
            long total = 0;
            for (SevenZArchiveEntry e : z.getEntries()) total += Math.max(0, e.getSize());
            long done = 0;
            SevenZArchiveEntry e;
            byte[] buf = new byte[BUF];
            while ((e = z.getNextEntry()) != null) {
                token.throwIfCancelled();
                File out = FileX.safeResolve(destDir, e.getName());
                if (e.isDirectory()) {
                    FileX.ensureDir(out);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) FileX.ensureDir(parent);
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), BUF)) {
                    int read;
                    long lastReport = done;
                    long lastDiskCheck = done;
                    while ((read = z.read(buf, 0, buf.length)) > 0) {
                        token.throwIfCancelled();
                        os.write(buf, 0, read);
                        done += read;
                        if (done - lastReport >= REPORT_STEP) {
                            lastReport = done;
                            if (sink != null) sink.progress(done, total, e.getName());
                        }
                        if (done - lastDiskCheck >= DISK_CHECK_STEP) {
                            lastDiskCheck = done;
                            checkFreeSpace(destDir);
                        }
                    }
                }
                if (e.getHasLastModifiedDate() && e.getLastModifiedDate().getTime() > 0) out.setLastModified(e.getLastModifiedDate().getTime());
                if (sink != null) sink.progress(done, total, e.getName());
            }
        } catch (IOException e) {
            throw wrap(e);
        } finally {
            closeQuietly(z);
        }
    }

    // ------------------------------------------------------------------ tar & single stream

    private static TarArchiveInputStream openTar(File archive) throws IOException {
        String name = archive.getName().toLowerCase();
        InputStream raw;
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            raw = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(archive), BUF));
        } else if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz")) {
            raw = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(archive), BUF));
        } else if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            raw = new XZCompressorInputStream(new BufferedInputStream(new FileInputStream(archive), BUF));
        } else {
            raw = new BufferedInputStream(new FileInputStream(archive), BUF);
        }
        return new TarArchiveInputStream(raw, "UTF-8");
    }

    private static void extractTar(File archive, File destDir, ProgressSink sink, CancelToken token) throws IOException {
        TarArchiveInputStream tis = openTar(archive);
        try {
            byte[] buf = new byte[BUF];
            TarArchiveEntry e;
            while ((e = tis.getNextEntry()) != null) {
                token.throwIfCancelled();
                File out = FileX.safeResolve(destDir, e.getName());
                if (e.isDirectory()) {
                    FileX.ensureDir(out);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) FileX.ensureDir(parent);
                try (OutputStream os = new CountingOutputStream(
                        new BufferedOutputStream(new FileOutputStream(out), BUF), token, null, 0, 0, null, destDir)) {
                    int read;
                    while ((read = tis.read(buf, 0, buf.length)) > 0) {
                        token.throwIfCancelled();
                        os.write(buf, 0, read);
                    }
                }
                if (e.getModTime() != null && e.getModTime().getTime() > 0) out.setLastModified(e.getModTime().getTime());
                if (sink != null) sink.progress(tis.getBytesRead(), 0, e.getName());
            }
        } catch (IOException e) {
            throw wrap(e);
        } finally {
            closeQuietly(tis);
        }
    }

    private static InputStream openSingleStream(File archive, ArchiveKind kind) throws IOException {
        InputStream raw = new BufferedInputStream(new FileInputStream(archive), BUF);
        switch (kind) {
            case GZIP: return new GzipCompressorInputStream(raw);
            case BZIP2: return new BZip2CompressorInputStream(raw);
            case XZ: return new XZCompressorInputStream(raw);
            default: return raw;
        }
    }

    private static void extractSingleStream(File archive, File destDir, ArchiveKind kind,
                                            ProgressSink sink, CancelToken token) throws IOException {
        InputStream dec = openSingleStream(archive, kind);
        BufferedInputStream peek = new BufferedInputStream(dec, BUF);
        try {
            peek.mark(4096);
            byte[] head = new byte[512];
            int headLen = 0;
            while (headLen < head.length) {
                int r = peek.read(head, headLen, head.length - headLen);
                if (r <= 0) break;
                headLen += r;
            }
            peek.reset();
            if (isTarHeader(head, headLen)) {
                TarArchiveInputStream tis = new TarArchiveInputStream(peek, "UTF-8");
                try {
                    extractFromTarStream(tis, destDir, sink, token);
                } finally {
                    closeQuietly(tis);
                }
            } else {
                String outName = FileX.stripExtension(archive.getName());
                if (outName.isEmpty()) outName = archive.getName() + ".out";
                File out = FileX.uniqueFile(destDir, outName);
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), BUF)) {
                    copy(peek, os, token, archive.length(), 0, out.getName(), sink, destDir);
                }
            }
        } finally {
            closeQuietly(peek);
        }
    }

    private static void extractFromTarStream(TarArchiveInputStream tis, File destDir,
                                             ProgressSink sink, CancelToken token) throws IOException {
        byte[] buf = new byte[BUF];
        TarArchiveEntry e;
        while ((e = tis.getNextEntry()) != null) {
            token.throwIfCancelled();
            File out = FileX.safeResolve(destDir, e.getName());
            if (e.isDirectory()) {
                FileX.ensureDir(out);
                continue;
            }
            File parent = out.getParentFile();
            if (parent != null) FileX.ensureDir(parent);
            try (OutputStream os = new CountingOutputStream(
                    new BufferedOutputStream(new FileOutputStream(out), BUF), token, null, 0, 0, null, destDir)) {
                int read;
                while ((read = tis.read(buf, 0, buf.length)) > 0) {
                    token.throwIfCancelled();
                    os.write(buf, 0, read);
                }
            }
            if (e.getModTime() != null && e.getModTime().getTime() > 0) out.setLastModified(e.getModTime().getTime());
            if (sink != null) sink.progress(tis.getBytesRead(), 0, e.getName());
        }
    }

    private static boolean isTarHeader(byte[] head, int len) {
        if (len < 263) return false;
        return head[257] == 'u' && head[258] == 's' && head[259] == 't'
                && head[260] == 'a' && head[261] == 'r';
    }

    // ------------------------------------------------------------------ helpers

    private static long copy(InputStream in, OutputStream out, CancelToken token,
                             long total, long baseDone, String label, ProgressSink sink,
                             File guardDir) throws IOException {
        byte[] buf = new byte[BUF];
        long copied = 0;
        long lastReport = 0;
        long lastDiskCheck = 0;
        int read;
        while ((read = in.read(buf)) > 0) {
            token.throwIfCancelled();
            out.write(buf, 0, read);
            copied += read;
            if (copied - lastReport >= REPORT_STEP) {
                lastReport = copied;
                if (sink != null) sink.progress(baseDone + copied, total, label);
            }
            if (guardDir != null && copied - lastDiskCheck >= DISK_CHECK_STEP) {
                lastDiskCheck = copied;
                checkFreeSpace(guardDir);
            }
        }
        return copied;
    }

    /** Aborts before the device runs out of storage (a full disk destabilises the whole system). */
    private static void checkFreeSpace(File dir) throws IOException {
        if (dir == null) return;
        long free = dir.getUsableSpace();
        if (free > 0 && free < MIN_FREE_BYTES) {
            throw new IOException("存储空间不足（剩余 " + FileX.formatSize(free) + "），已停止解压");
        }
    }

    /** Streams to a file while reporting progress and honouring cancellation (RAR needs a stream). */
    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final CancelToken token;
        private final ProgressSink sink;
        private final long total;
        private final long baseDone;
        private final String label;
        private final File guardDir;
        private long written;
        private long lastReport;
        private long lastDiskCheck;

        CountingOutputStream(OutputStream delegate, CancelToken token, ProgressSink sink,
                             long total, long baseDone, String label, File guardDir) {
            this.delegate = delegate;
            this.guardDir = guardDir;
            this.token = token;
            this.sink = sink;
            this.total = total;
            this.baseDone = baseDone;
            this.label = label;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (token != null && token.isCancelled()) {
                throw new OperationCancelledException();
            }
            delegate.write(b, off, len);
            written += len;
            if (sink != null && written - lastReport >= REPORT_STEP) {
                lastReport = written;
                sink.progress(baseDone + written, total, label);
            }
            if (written - lastDiskCheck >= DISK_CHECK_STEP) {
                lastDiskCheck = written;
                checkFreeSpace(guardDir);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) return;
        try {
            if (closeable instanceof java.io.Closeable) {
                ((java.io.Closeable) closeable).close();
            } else if (closeable instanceof AutoCloseable) {
                ((AutoCloseable) closeable).close();
            }
        } catch (Exception ignored) {
            // nothing useful to do
        }
    }

    /** Wraps a library exception, but lets a user cancellation keep its type. */
    private static IOException wrap(IOException e) {
        if (e instanceof OperationCancelledException) return e;
        return new IOException(describe(e), e);
    }

    /** Maps library exceptions to messages the UI can show as-is. */
    public static String describe(Throwable e) {
        String msg = e == null ? "" : String.valueOf(e.getMessage());
        String lower = msg.toLowerCase();
        if (lower.contains("password") || lower.contains("encrypted") || lower.contains("decrypt")
                || lower.contains("crc") || lower.contains("checksum")) {
            return "密码错误或压缩包已损坏";
        }
        if (lower.contains("unsupported") || lower.contains("not supported")) {
            return "该压缩包使用了不支持的算法";
        }
        if (msg.isEmpty()) return e.getClass().getSimpleName();
        return msg;
    }
}
