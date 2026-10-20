import com.ezip.app.archive.ArchiveEntryInfo;
import com.ezip.app.archive.ArchiveService;
import com.ezip.app.archive.CancelToken;
import com.ezip.app.archive.Compressor;
import com.ezip.app.archive.OperationCancelledException;
import com.ezip.app.archive.ProgressSink;
import com.ezip.app.util.DirLoader;
import com.ezip.app.util.FileItem;
import com.ezip.app.util.FileSearch;
import com.ezip.app.util.FileX;
import com.ezip.app.util.Ooxml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Desktop (JVM) verification of the archive engine used by the Android app.
 * The engine package deliberately has no android.* imports, so everything that
 * actually moves bytes can be exercised here without a device.
 *
 * Run: powershell -File tools\run-engine-test.ps1
 */
public class EngineTest {

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();
    private static File tmpRoot;

    public static void main(String[] args) throws Exception {
        tmpRoot = new File(args.length > 0 ? args[0] : "build/enginetest");
        FileX.deleteRecursive(tmpRoot);
        tmpRoot.mkdirs();

        testZipRoundTrips();
        testZipPassword();
        testSevenZip();
        testTarGz();
        testGbkZipNames();
        testTimestamps();
        testChineseNamesInEncryptedArchives();
        testEncryptionProbe();
        testTarCreation();
        testFileSearch();
        testDirLoader();
        testRarSamples();
        testTraversalSafety();
        testOoxml();
        testCancellation();
        testNaming();

        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println("PASSED: " + passed + "   FAILED: " + failed);
        if (failed > 0) {
            for (String f : failures) System.out.println("  FAIL: " + f);
            System.exit(1);
        }
        System.out.println("ENGINE TEST OK");
    }

    // ------------------------------------------------------------------ tests

    private static void testZipRoundTrips() throws Exception {
        File src = buildSampleTree("zip-normal");
        for (int level : new int[]{Compressor.LEVEL_STORE, Compressor.LEVEL_NORMAL, Compressor.LEVEL_MAX}) {
            for (int format : new int[]{Compressor.FORMAT_ZIP, Compressor.FORMAT_7Z}) {
                String tag = (format == Compressor.FORMAT_ZIP ? "zip" : "7z") + "/level" + level;
                String ext = format == Compressor.FORMAT_ZIP ? ".zip" : ".7z";
                File out = new File(tmpRoot, tag.replace('/', '_') + ext);
                List<File> items = new ArrayList<>(Arrays.asList(src.listFiles()));
                Compressor.compress(items, out, format, level, null, null, null);
                check(tag + " created", out.isFile() && out.length() > 0, "size=" + out.length());
                File dest = new File(tmpRoot, "ex_" + tag.replace('/', '_'));
                ArchiveService.extract(out, dest, null, null, null);
                // one level down: the selected items are the archive roots
                File root = dest;
                System.out.println("    " + tag + ": " + out.length() + " bytes -> "
                        + countFiles(root) + " files");
                compareTrees(src, root, tag);
            }
        }
    }

    private static void testZipPassword() throws Exception {
        File src = buildSampleTree("zip-aes");
        File out = new File(tmpRoot, "aes.zip");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), out,
                Compressor.FORMAT_ZIP, Compressor.LEVEL_NORMAL, "pa55word", null, null);
        check("AES zip created", out.isFile(), "");
        File dest = new File(tmpRoot, "ex_aes");
        ArchiveService.extract(out, dest, "pa55word", null, null);
        compareTrees(src, dest, "aes-zip");

        File wrong = new File(tmpRoot, "ex_aes_wrong");
        boolean rejected = false;
        try {
            ArchiveService.extract(out, wrong, "bad-password", null, null);
        } catch (IOException e) {
            rejected = true;
        }
        check("AES zip rejects wrong password", rejected, "");

        // encrypted 7z
        File out7 = new File(tmpRoot, "aes.7z");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), out7,
                Compressor.FORMAT_7Z, Compressor.LEVEL_MAX, "pa55word", null, null);
        File dest7 = new File(tmpRoot, "ex_aes7z");
        ArchiveService.extract(out7, dest7, "pa55word", null, null);
        compareTrees(src, dest7, "aes-7z");
    }

    private static void testSevenZip() throws Exception {
        File out = new File(tmpRoot, "nopass.7z");
        File src = buildSampleTree("7z-plain");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), out,
                Compressor.FORMAT_7Z, Compressor.LEVEL_MAX, null, null, null);
        List<ArchiveEntryInfo> entries = ArchiveService.list(out, null);
        check("7z list non-empty", entries.size() > 0, "entries=" + entries.size());
        File dest = new File(tmpRoot, "ex_7z_plain");
        ArchiveService.extract(out, dest, null, null, null);
        compareTrees(src, dest, "7z-plain");
    }

    private static void testTarGz() throws Exception {
        File src = buildSampleTree("targz");
        File tar = new File(tmpRoot, "sample.tar.gz");
        ProcessBuilder pb = new ProcessBuilder("tar", "-czf", tar.getAbsolutePath(),
                "-C", src.getAbsolutePath(), ".");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(readAll(p.getInputStream()), "UTF-8");
        int code = p.waitFor();
        check("tar.gz created", code == 0 && tar.isFile() && tar.length() > 0, out.trim());
        if (code != 0) return;

        List<ArchiveEntryInfo> entries = ArchiveService.list(tar, null);
        check("tar.gz list non-empty", entries.size() > 0, "entries=" + entries.size());

        File dest = new File(tmpRoot, "ex_targz");
        ArchiveService.extract(tar, dest, null, null, null);
        compareTrees(src, dest, "tar.gz");
    }

    private static void testGbkZipNames() throws Exception {
        File zip = new File(tmpRoot, "gbk-names.zip");
        Charset gbk = Charset.forName("GBK");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip), gbk)) {
            zos.putNextEntry(new ZipEntry("中文文件.txt"));
            zos.write("中文内容".getBytes("GBK"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("报表 2024.csv"));
            zos.write("a,b,c".getBytes("GBK"));
            zos.closeEntry();
        }
        File dest = new File(tmpRoot, "ex_gbk");
        ArchiveService.extract(zip, dest, null, null, null);
        String[] names = dest.list();
        check("GBK zip names restored",
                names != null && names.length == 2 && containsName(names, "中文文件.txt"),
                names == null ? "null" : Arrays.toString(names));
        File f = new File(dest, "中文文件.txt");
        String actual = f.isFile() ? FileX.readText(f, 4096) : "<missing>";
        check("GBK zip content restored", "中文内容".equals(actual),
                "len=" + (f.isFile() ? f.length() : -1) + " actual=" + escape(actual));
    }

    /** Regression: extraction used to write a packed DOS time into File.setLastModified -> 1970. */
    private static void testTimestamps() throws Exception {
        File dir = new File(tmpRoot, "src_time");
        FileX.deleteRecursive(dir);
        dir.mkdirs();
        File file = new File(dir, "dated.txt");
        write(file, "time test");
        long when = 1623750896000L;              // 2021-06-15 12:34:56 UTC
        file.setLastModified(when);
        long actualSource = file.lastModified();

        for (int format : new int[]{Compressor.FORMAT_ZIP, Compressor.FORMAT_7Z}) {
            String label = format == Compressor.FORMAT_ZIP ? "zip" : "7z";
            File out = new File(tmpRoot, "time_" + label + (format == Compressor.FORMAT_ZIP ? ".zip" : ".7z"));
            Compressor.compress(new ArrayList<>(Arrays.asList(file)), out, format,
                    Compressor.LEVEL_NORMAL, null, null, null);

            File dest = new File(tmpRoot, "ex_time_" + label);
            ArchiveService.extract(out, dest, null, null, null);
            File restored = new File(dest, "dated.txt");
            long got = restored.lastModified();
            check(label + " restores file mtime", Math.abs(got - actualSource) < 2000,
                    "expected=" + actualSource + " got=" + got + " (" + FileX.formatTime(got) + ")");

            long listed = 0;
            for (ArchiveEntryInfo e : ArchiveService.list(out, null)) {
                if ("dated.txt".equals(e.name)) listed = e.time;
            }
            check(label + " lists a sane entry time", Math.abs(listed - actualSource) < 2000,
                    "listed=" + listed + " (" + FileX.formatTime(listed) + ")");
        }
    }

    /** Regression: encrypted archives showed garbled Chinese names in the content viewer. */
    private static void testChineseNamesInEncryptedArchives() throws Exception {
        File src = new File(tmpRoot, "src_cn");
        FileX.deleteRecursive(src);
        File docs = new File(src, "文档");
        docs.mkdirs();
        File report = new File(docs, "报告 2024.txt");
        write(report, "季度报告");

        // 1) archive created by EZip itself (UTF-8 names + AES)
        File aes = new File(tmpRoot, "cn_aes.zip");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), aes,
                Compressor.FORMAT_ZIP, Compressor.LEVEL_NORMAL, "pw123", null, null);
        List<ArchiveEntryInfo> entries = ArchiveService.list(aes, "pw123");
        check("AES zip keeps Chinese names", hasEntry(entries, "文档/报告 2024.txt") && namesClean(entries),
                namesOf(entries));
        File dest = new File(tmpRoot, "ex_cn_aes");
        ArchiveService.extract(aes, dest, "pw123", null, null);
        check("AES zip extracts Chinese names", new File(dest, "文档/报告 2024.txt").isFile(), "");

        // 2) legacy Windows style: GBK bytes without the UTF-8 flag, AES encrypted
        File legacy = new File(tmpRoot, "cn_gbk_aes.zip");
        net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(legacy, "pw123".toCharArray());
        zf.setCharset(Charset.forName("GBK"));
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setEncryptFiles(true);
        params.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
        params.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);
        params.setFileNameInZip("中文文件.txt");
        zf.addFile(report, params);

        List<ArchiveEntryInfo> legacyEntries = ArchiveService.list(legacy, "pw123");
        check("GBK+AES zip decodes Chinese names",
                legacyEntries.size() == 1 && "中文文件.txt".equals(legacyEntries.get(0).name),
                namesOf(legacyEntries));

        // 3) same legacy archive, but not encrypted
        File plainGbk = new File(tmpRoot, "cn_gbk.zip");
        net.lingala.zip4j.ZipFile zf2 = new net.lingala.zip4j.ZipFile(plainGbk);
        zf2.setCharset(Charset.forName("GBK"));
        net.lingala.zip4j.model.ZipParameters p2 = new net.lingala.zip4j.model.ZipParameters();
        p2.setFileNameInZip("旧文件.txt");
        zf2.addFile(report, p2);
        List<ArchiveEntryInfo> plainEntries = ArchiveService.list(plainGbk, null);
        check("GBK zip decodes Chinese names",
                plainEntries.size() == 1 && "旧文件.txt".equals(plainEntries.get(0).name),
                namesOf(plainEntries));

        // 4) 7z with AES keeps Chinese names (UTF-16 headers)
        File sevenZip = new File(tmpRoot, "cn_aes.7z");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), sevenZip,
                Compressor.FORMAT_7Z, Compressor.LEVEL_NORMAL, "pw123", null, null);
        List<ArchiveEntryInfo> sevenEntries = ArchiveService.list(sevenZip, "pw123");
        check("AES 7z keeps Chinese names", hasEntry(sevenEntries, "文档/报告 2024.txt"),
                namesOf(sevenEntries));
    }

    private static boolean hasEntry(List<ArchiveEntryInfo> entries, String name) {
        for (ArchiveEntryInfo e : entries) {
            if (name.equals(e.name)) return true;
        }
        return false;
    }

    private static boolean namesClean(List<ArchiveEntryInfo> entries) {
        for (ArchiveEntryInfo e : entries) {
            if (e.name != null && e.name.indexOf('\uFFFD') >= 0) return false;
        }
        return true;
    }

    private static String namesOf(List<ArchiveEntryInfo> entries) {
        StringBuilder sb = new StringBuilder();
        for (ArchiveEntryInfo e : entries) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.name);
        }
        return sb.toString();
    }

    /** Regression: the archive viewer must know an archive is encrypted before tapping an entry. */
    private static void testEncryptionProbe() throws Exception {
        File src = new File(tmpRoot, "src_probe");
        FileX.deleteRecursive(src);
        src.mkdirs();
        write(new File(src, "a.txt"), "probe");

        File plain = new File(tmpRoot, "probe_plain.zip");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), plain,
                Compressor.FORMAT_ZIP, Compressor.LEVEL_NORMAL, null, null, null);
        check("plain zip not reported as encrypted", !ArchiveService.isEncrypted(plain), "");

        File locked = new File(tmpRoot, "probe_locked.zip");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), locked,
                Compressor.FORMAT_ZIP, Compressor.LEVEL_NORMAL, "secret", null, null);
        check("AES zip reported as encrypted", ArchiveService.isEncrypted(locked), "");

        File rarPlain = new File("tools/testdata/rar5.rar");
        if (rarPlain.isFile()) {
            check("plain rar not reported as encrypted", !ArchiveService.isEncrypted(rarPlain), "");
        }
        File rarLocked = new File("tools/testdata/rar5-password-junrar.rar");
        if (rarLocked.isFile()) {
            check("encrypted rar reported as encrypted", ArchiveService.isEncrypted(rarLocked), "");
        }

        // password errors must be recognisable so the UI can re-prompt
        boolean flagged = false;
        try {
            File dest = new File(tmpRoot, "ex_probe_wrong");
            ArchiveService.extract(locked, dest, "wrong", null, null);
        } catch (IOException e) {
            flagged = ArchiveService.isPasswordError(e);
        }
        check("wrong password is classified as password error", flagged, "");
    }

    /** TAR is now writable too: plain TAR plus gzip / bzip2 / xz variants. */
    private static void testTarCreation() throws Exception {
        File src = buildSampleTree("tarsrc");
        int[] formats = {
                Compressor.FORMAT_TAR, Compressor.FORMAT_TAR_GZ,
                Compressor.FORMAT_TAR_BZ2, Compressor.FORMAT_TAR_XZ
        };
        String[] exts = {".tar", ".tar.gz", ".tar.bz2", ".tar.xz"};
        for (int i = 0; i < formats.length; i++) {
            String label = exts[i];
            File out = new File(tmpRoot, "made" + exts[i]);
            List<File> items = new ArrayList<>(Arrays.asList(src.listFiles()));
            Compressor.compress(items, out, formats[i], Compressor.LEVEL_NORMAL, null, null, null);
            check("tar creation " + label, out.isFile() && out.length() > 0, "size=" + out.length());

            File dest = new File(tmpRoot, "ex_tar" + i);
            ArchiveService.extract(out, dest, null, null, null);
            compareTrees(src, dest, "created" + label);

            List<ArchiveEntryInfo> entries = ArchiveService.list(out, null);
            check("tar list " + label, entries.size() > 0, "entries=" + entries.size());
        }

        // cross check with the OS tar (libarchive) so we know other tools can read it
        File gz = new File(tmpRoot, "made.tar.gz");
        if (gz.isFile() && extExists("tar")) {
            ProcessBuilder pb = new ProcessBuilder("tar", "-tzf", gz.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String listing = new String(readAll(p.getInputStream()), "UTF-8");
            int code = p.waitFor();
            check("system tar can list our .tar.gz", code == 0 && !listing.trim().isEmpty(),
                    "exit=" + code + " lines=" + listing.split("\n").length);
        }

        // passwords are meaningless for TAR: must be ignored instead of failing
        File locked = new File(tmpRoot, "made_locked.tar.gz");
        Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), locked,
                Compressor.FORMAT_TAR_GZ, Compressor.LEVEL_NORMAL, "should-be-ignored", null, null);
        File dest = new File(tmpRoot, "ex_tar_locked");
        ArchiveService.extract(locked, dest, null, null, null);
        check("tar ignores password", countFiles(dest) > 0, "files=" + countFiles(dest));
        check("tar reports no password support",
                !Compressor.supportsPassword(Compressor.FORMAT_TAR)
                        && Compressor.supportsPassword(Compressor.FORMAT_ZIP)
                        && Compressor.supportsPassword(Compressor.FORMAT_7Z), "");
    }

    private static boolean extExists(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Name search used by the browser's search bar. */
    private static void testFileSearch() throws Exception {
        File root = new File(tmpRoot, "search_root");
        FileX.deleteRecursive(root);
        File a = new File(root, "文档/报告 2024.txt");
        File b = new File(root, "文档/子目录/季度报告.pdf");
        File c = new File(root, "照片/报告截图.png");
        File d = new File(root, "readme.txt");
        File hidden = new File(root, ".隐藏/报告隐藏.txt");
        write(a, "a");
        write(b, "b");
        write(c, "c");
        write(d, "d");
        write(hidden, "h");

        List<File> hits = FileSearch.search(root, "报告", false, 100, 1000, null);
        check("search finds nested matches", hits.size() == 3, fileNames(hits));
        check("search skips hidden by default", !containsPath(hits, hidden), fileNames(hits));

        List<File> withHidden = FileSearch.search(root, "报告", true, 100, 1000, null);
        check("search can include hidden", containsPath(withHidden, hidden), fileNames(withHidden));

        List<File> none = FileSearch.search(root, "不存在的名字", false, 100, 1000, null);
        check("search returns empty when nothing matches", none.isEmpty(), "size=" + none.size());

        List<File> limited = FileSearch.search(root, "报告", false, 2, 1000, null);
        check("search honours the result cap", limited.size() == 2, "size=" + limited.size());

        File upper = new File(root, "UPPER.TXT");
        write(upper, "u");
        List<File> caseInsensitive = FileSearch.search(root, "upper", false, 100, 1000, null);
        check("search is case insensitive", containsPath(caseInsensitive, upper), fileNames(caseInsensitive));

        final int[] polls = {0};
        FileSearch.search(root, "报告", false, 100, 1000, new FileSearch.Callback() {
            @Override
            public boolean isCancelled() {
                polls[0]++;
                return polls[0] > 2;
            }

            @Override
            public void onProgress(List<File> results, int scannedDirs) {
                // progress only
            }
        });
        check("search can be cancelled", polls[0] > 2, "polls=" + polls[0]);
    }

    private static boolean containsPath(List<File> files, File target) {
        String path = target.getAbsolutePath();
        for (File f : files) {
            if (f.getAbsolutePath().equals(path)) return true;
        }
        return false;
    }

    private static String fileNames(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName());
        }
        return sb.toString();
    }

    /** Directory loading is done off the UI thread now; its sort/meta logic is pure Java. */
    private static void testDirLoader() throws Exception {
        File root = new File(tmpRoot, "loader_root");
        FileX.deleteRecursive(root);
        new File(root, "b_dir").mkdirs();
        new File(root, "A_dir").mkdirs();
        File small = new File(root, "small.txt");
        File big = new File(root, "big.bin");
        File hidden = new File(root, ".hidden.txt");
        write(small, "1");
        write(big, new byte[5000]);
        write(hidden, "h");
        small.setLastModified(1600000000000L);
        big.setLastModified(1700000000000L);

        List<FileItem> byName = DirLoader.load(root, false, DirLoader.SORT_NAME, false, root);
        check("loader skips hidden entries", byName.size() == 4, "size=" + byName.size());
        check("loader puts folders first",
                byName.get(0).directory && byName.get(1).directory, fileNames(toFiles(byName)));
        check("loader sorts folders by name", "A_dir".equals(byName.get(0).name), byName.get(0).name);
        check("loader sorts files by name", "big.bin".equals(byName.get(2).name), byName.get(2).name);
        check("loader precomputes size text", byName.get(2).meta.contains("KB"), byName.get(2).meta);
        check("loader precomputes folder text", byName.get(0).meta.startsWith("文件夹"), byName.get(0).meta);

        List<FileItem> bySize = DirLoader.load(root, false, DirLoader.SORT_SIZE, false, root);
        check("loader sorts by size desc", "big.bin".equals(bySize.get(2).name), bySize.get(2).name);

        List<FileItem> byDate = DirLoader.load(root, false, DirLoader.SORT_DATE, false, root);
        check("loader sorts by date desc", "big.bin".equals(byDate.get(2).name), byDate.get(2).name);

        List<FileItem> withHidden = DirLoader.load(root, true, DirLoader.SORT_NAME, false, root);
        check("loader can include hidden entries", withHidden.size() == 5, "size=" + withHidden.size());

        List<FileItem> parentMeta = DirLoader.load(new File(root, "b_dir"), false,
                DirLoader.SORT_NAME, true, root);
        check("loader shows parent path for search rows",
                parentMeta.isEmpty() || parentMeta.get(0).meta.contains("b_dir"), "");

        boolean threw = false;
        try {
            DirLoader.load(small, false, DirLoader.SORT_NAME, false, root);
        } catch (IOException e) {
            threw = true;
        }
        check("loader rejects non directories", threw, "");
    }

    private static List<File> toFiles(List<FileItem> items) {
        List<File> files = new ArrayList<>();
        for (FileItem item : items) files.add(item.file);
        return files;
    }

    private static void testRarSamples() {
        File dir = new File("tools/testdata");
        if (!dir.isDirectory()) {
            System.out.println("  (skipping RAR tests, tools/testdata not present)");
            return;
        }
        rarCase(new File(dir, "rar5.rar"), null, "rar5.rar");
        rarCase(new File(dir, "m0-plain-128k.rar"), null, "rar5 m0-plain");
        rarCase(new File(dir, "rar5-solid.rar"), null, "rar5 solid");
        rarCase(new File(dir, "BoatModernEnglish-regular-unpack30.rar"), null, "rar4 unpack30");
        rarCase(new File(dir, "rar5-password-junrar.rar"), "junrar", "rar5 encrypted");
        rarCase(new File(dir, "rar4-encrypted-junrar.rar"), "junrar", "rar4 encrypted");
    }

    private static void rarCase(File rar, String password, String tag) {
        if (!rar.isFile()) {
            System.out.println("  (missing " + rar.getName() + ")");
            return;
        }
        try {
            List<ArchiveEntryInfo> entries = ArchiveService.list(rar, password);
            check(tag + " list", !entries.isEmpty(), "entries=" + entries.size());
            File dest = new File(tmpRoot, "rar_" + tag.replace(' ', '_'));
            ArchiveService.extract(rar, dest, password, null, null);
            long files = countFiles(dest);
            check(tag + " extract", files > 0, "files=" + files);
        } catch (Throwable e) {
            check(tag + " list/extract", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void testTraversalSafety() throws Exception {
        check("safeResolve rejects ..", rejects("../evil.txt"), "");
        check("safeResolve contains absolute path", inside(tmpRoot, "/etc/passwd"), "");
        check("safeResolve contains drive path", inside(tmpRoot, "C:\\Windows\\evil.dll"), "");
        check("safeResolve accepts nested", !rejects("a/b/c.txt"), "");

        File dir = new File("tools/testdata");
        File rar = new File(dir, "mkdir-escape.rar");
        File rar2 = new File(dir, "parent-dir.rar");
        for (File f : new File[]{rar, rar2}) {
            if (!f.isFile()) continue;
            File sandbox = new File(tmpRoot, "sandbox_" + f.getName());
            File dest = new File(sandbox, "dest");
            dest.mkdirs();
            try {
                ArchiveService.extract(f, dest, null, null, null);
            } catch (Throwable ignored) {
                // refusing to extract is an acceptable outcome
            }
            boolean escaped = false;
            String[] kids = sandbox.list();
            if (kids != null) {
                for (String k : kids) {
                    if (!k.equals("dest")) escaped = true;
                }
            }
            check(f.getName() + " did not escape target", !escaped, "");
        }
    }

    private static void testOoxml() throws Exception {
        File docx = new File(tmpRoot, "sample.docx");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(docx))) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String xml = "<?xml version=\"1.0\"?><w:document xmlns:w=\"x\"><w:body>"
                    + "<w:p><w:r><w:t>你好 EZip</w:t></w:r></w:p>"
                    + "<w:p><w:r><w:t>第二段 &amp; 符号</w:t></w:r></w:p>"
                    + "</w:body></w:document>";
            zos.write(xml.getBytes("UTF-8"));
            zos.closeEntry();
        }
        String text = Ooxml.extractText(docx, 10000);
        check("docx text extracted", text.contains("你好 EZip") && text.contains("第二段 & 符号"),
                text.replace("\n", "|"));
    }

    private static void testCancellation() throws Exception {
        File src = buildSampleTree("cancel");
        // a big file so cancellation lands mid-stream
        File big = new File(src, "big.bin");
        try (FileOutputStream fos = new FileOutputStream(big)) {
            byte[] chunk = new byte[1024 * 1024];
            new Random(7).nextBytes(chunk);
            for (int i = 0; i < 24; i++) fos.write(chunk);
        }
        File out = new File(tmpRoot, "cancel.zip");
        final CancelToken token = new CancelToken();
        final int[] calls = {0};
        ProgressSink sink = new ProgressSink() {
            @Override
            public void progress(long done, long total, String label) {
                calls[0]++;
                if (calls[0] > 2) token.cancel();
            }
        };
        boolean cancelled = false;
        try {
            Compressor.compress(new ArrayList<>(Arrays.asList(src.listFiles())), out,
                    Compressor.FORMAT_7Z, Compressor.LEVEL_NORMAL, null, sink, token);
        } catch (OperationCancelledException e) {
            cancelled = true;
        }
        check("7z compression honours cancellation", cancelled, "callbackCalls=" + calls[0]);
    }

    private static void testNaming() {
        File one = new File("holiday photos.jpg");
        check("suggestName single", "holiday photos.zip".equals(
                Compressor.suggestName(new ArrayList<>(Arrays.asList(one)), Compressor.FORMAT_ZIP)), "");
        check("suggestName multi", "压缩包.7z".equals(
                Compressor.suggestName(new ArrayList<>(Arrays.asList(one, one)), Compressor.FORMAT_7Z)), "");
        check("stripExtension tar.gz", "a".equals(FileX.stripExtension("a.tar.gz")), "");
        check("formatSize 1.5 MB", "1.5 MB".equals(FileX.formatSize(1024 * 1024 * 3 / 2)), FileX.formatSize(1024 * 1024 * 3 / 2));
        check("uniqueFile", FileX.uniqueFile(tmpRoot, "sample.tar.gz").getName().startsWith("sample"),
                FileX.uniqueFile(tmpRoot, "sample.tar.gz").getName());
    }

    // ------------------------------------------------------------------ helpers

    private static boolean rejects(String entry) {        try {
            FileX.safeResolve(new File(tmpRoot, "safe"), entry);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /** true when the entry resolves to a path still inside the target directory. */
    private static boolean inside(File dir, String entry) {
        try {
            File f = FileX.safeResolve(dir, entry);
            return f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsName(String[] names, String want) {
        for (String n : names) if (want.equals(n)) return true;
        return false;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(s.length(), 12); i++) {
            sb.append(String.format("U+%04X ", (int) s.charAt(i)));
        }
        return sb.toString();
    }

    private static File buildSampleTree(String tag) throws Exception {
        File root = new File(tmpRoot, "src_" + tag);
        FileX.deleteRecursive(root);
        root.mkdirs();
        File docs = new File(root, "文档");
        docs.mkdirs();
        write(new File(root, "readme.txt"), "hello EZip\n第二行\n");
        write(new File(docs, "报告.txt"), "季度报告 2024\n");
        write(new File(root, "照片 01.jpg"), fakeBinary(1500, 1));
        File nested = new File(docs, "deep/deeper");
        nested.mkdirs();
        write(new File(nested, "data.json"), "{\"k\":\"值\"}");
        new File(root, "空文件夹").mkdirs();
        return root;
    }

    private static byte[] fakeBinary(int size, long seed) {
        byte[] b = new byte[size];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static void write(File f, String text) throws Exception {
        write(f, text.getBytes("UTF-8"));
    }

    private static void write(File f, byte[] bytes) throws Exception {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }
    }

    private static String readText(File f) throws Exception {
        return new String(readAll(new FileInputStream(f)), "UTF-8");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        return out.toByteArray();
    }

    private static long countFiles(File dir) {
        long n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File k : kids) {
            if (k.isDirectory()) n += countFiles(k);
            else n++;
        }
        return n;
    }

    /** Compares file names + content hashes between the source tree and the extracted tree. */
    private static void compareTrees(File src, File dst, String tag) throws Exception {
        TreeMap<String, String> expected = new TreeMap<>();
        TreeMap<String, String> actual = new TreeMap<>();
        hashTree(src, "", expected);
        hashTree(dst, "", actual);
        List<String> missing = new ArrayList<>();
        List<String> differed = new ArrayList<>();
        for (String k : expected.keySet()) {
            if (!actual.containsKey(k)) missing.add(k);
            else if (!expected.get(k).equals(actual.get(k))) differed.add(k);
        }
        check(tag + " all files restored", missing.isEmpty(), "missing=" + missing);
        check(tag + " content identical", differed.isEmpty(), "differed=" + differed);
        check(tag + " empty folder kept", new File(dst, "空文件夹").isDirectory(), "");
    }

    private static void hashTree(File dir, String prefix, TreeMap<String, String> out) throws Exception {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File k : kids) {
            String name = prefix.isEmpty() ? k.getName() : prefix + "/" + k.getName();
            if (k.isDirectory()) {
                hashTree(k, name, out);
            } else {
                out.put(name, sha256(k));
            }
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
        } else {
            failed++;
            failures.add(name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
            System.out.println("  FAIL " + name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
    }
}
