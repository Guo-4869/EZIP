import com.ezip.app.util.DirLoader;
import com.ezip.app.util.FileItem;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Micro benchmark: old UI-thread path (listFiles + stat inside the comparator + per-row stat
 * and a nested listFiles per folder row) versus the new precomputed DirLoader.
 * Windows stat is cached, so on Android the gap is larger than what this prints.
 */
public class BenchDir {

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        File dir = new File("build/benchdir");
        prepare(dir, count);

        for (int i = 0; i < 3; i++) {          // warm up the file cache
            oldPath(dir);
            newPath(dir);
        }
        long t0 = System.nanoTime();
        oldPath(dir);
        long t1 = System.nanoTime();
        newPath(dir);
        long t2 = System.nanoTime();

        System.out.printf("files=%d  old=%.1f ms   new=%.1f ms   speedup=%.1fx%n",
                count, (t1 - t0) / 1e6, (t2 - t1) / 1e6,
                (double) (t1 - t0) / Math.max(1, (t2 - t1)));
    }

    /** Simulates the previous implementation. */
    private static void oldPath(File dir) {
        File[] all = dir.listFiles();
        if (all == null) return;
        List<File> visible = new ArrayList<>(all.length);
        for (File f : all) {
            if (!f.getName().startsWith(".")) visible.add(f);
        }
        Collections.sort(visible, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                boolean ad = a.isDirectory();
                boolean bd = b.isDirectory();
                if (ad != bd) return ad ? -1 : 1;
                if (ad) return a.getName().compareToIgnoreCase(b.getName());
                long al = a.length();
                long bl = b.length();
                if (al != bl) return al > bl ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        long sink = 0;
        // simulating ~20 visible rows being bound, including the nested listFiles per folder
        for (int i = 0; i < Math.min(20, visible.size()); i++) {
            File f = visible.get(i);
            sink += f.length() + f.lastModified();
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) sink += kids.length;
            }
        }
        if (sink == -1) System.out.print("");
    }

    private static void newPath(File dir) throws Exception {
        List<FileItem> items = DirLoader.load(dir, false, DirLoader.SORT_SIZE, false, dir);
        long sink = 0;
        for (int i = 0; i < Math.min(20, items.size()); i++) {
            FileItem item = items.get(i);
            sink += item.size + item.time + item.meta.length();
        }
        if (sink == -1) System.out.print("");
    }

    private static void prepare(File dir, int count) throws Exception {
        if (dir.isDirectory() && dir.listFiles() != null && dir.listFiles().length == count) return;
        delete(dir);
        dir.mkdirs();
        byte[] data = new byte[256];
        for (int i = 0; i < count; i++) {
            if (i % 10 == 0) {
                new File(dir, "dir_" + String.format("%05d", i)).mkdirs();
            } else {
                File f = new File(dir, "file_" + String.format("%05d", i) + ".dat");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
            }
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) delete(kid);
        }
        file.delete();
    }
}
