package com.ezip.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ezip.app.archive.ArchiveKind;
import com.ezip.app.archive.ArchiveService;
import com.ezip.app.archive.Compressor;
import com.ezip.app.archive.OperationCancelledException;
import com.ezip.app.archive.ProgressSink;
import com.ezip.app.archive.CancelToken;
import com.ezip.app.ui.Dialogs;
import com.ezip.app.ui.FileAdapter;
import com.ezip.app.ui.TaskRunner;
import com.ezip.app.ui.U;
import com.ezip.app.util.DirLoader;
import com.ezip.app.util.FileItem;
import com.ezip.app.util.FileSearch;
import com.ezip.app.util.FileX;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** File browser: browse, multi select, compress, extract, preview. */
public class MainActivity extends Activity implements FileAdapter.Listener {

    private static final int SORT_NAME = 0;
    private static final int SORT_SIZE = 1;
    private static final int SORT_DATE = 2;
    private static final int REQ_PERMISSION = 1001;

    private File currentDir;
    private FileAdapter adapter;
    private ListView listView;
    private LinearLayout toolbar;
    private LinearLayout toolbarSelect;
    private LinearLayout actionBar;
    private LinearLayout permCard;
    private TextView txtPermStatus;
    private TextView txtPermDesc;
    private TextView btnGrant;
    private TextView btnAppSettings;
    private TextView txtSelCount;
    private TextView txtPath;
    private View emptyView;
    private int sortMode = SORT_NAME;
    private boolean showHidden;
    private boolean selectionMode;

    private FileAdapter.Listener listener;

    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private static final int SEARCH_MAX_RESULTS = 500;
    private static final int SEARCH_MAX_DIRS = 20000;
    private static final long SEARCH_DEBOUNCE_MS = 320;

    private LinearLayout searchBar;
    private EditText editSearch;
    private View btnSearchClose;
    private TextView txtEmpty;
    private boolean searchMode;
    private boolean suppressSearchWatcher;
    private Runnable pendingSearch;
    private volatile int searchGeneration;
    private int dirGeneration;
    private boolean needsRefresh;
    private String pendingFocusPath;

    /** Remembered scroll anchor per directory, so going back restores the previous position. */
    private final java.util.Map<String, ScrollState> scrollStates = new java.util.HashMap<>();
    /** Short lived listing cache: re-entering a folder shows up instantly instead of rescanning. */
    private final java.util.Map<String, CachedListing> listingCache = new java.util.HashMap<>();
    private static final long LISTING_CACHE_MS = 30_000L;
    private static final int LISTING_CACHE_MAX = 8;

    private static final class ScrollState {
        String anchorPath;   // first visible row, by path (survives reordering)
        int index;
        int top;
    }

    private static final class CachedListing {
        final List<FileItem> items;
        final long time;

        CachedListing(List<FileItem> items) {
            this.items = items;
            this.time = System.currentTimeMillis();
        }
    }
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.list);
        toolbar = (LinearLayout) findViewById(R.id.toolbar);
        toolbarSelect = (LinearLayout) findViewById(R.id.toolbarSelect);
        actionBar = (LinearLayout) findViewById(R.id.actionBar);
        permCard = (LinearLayout) findViewById(R.id.permCard);
        txtSelCount = (TextView) findViewById(R.id.txtSelCount);
        txtPath = (TextView) findViewById(R.id.txtPath);
        emptyView = findViewById(R.id.emptyView);
        txtPermStatus = (TextView) findViewById(R.id.txtPermStatus);
        txtPermDesc = (TextView) findViewById(R.id.txtPermDesc);
        btnGrant = (TextView) findViewById(R.id.btnGrant);
        btnAppSettings = (TextView) findViewById(R.id.btnAppSettings);
        searchBar = (LinearLayout) findViewById(R.id.searchBar);
        editSearch = (EditText) findViewById(R.id.editSearch);
        btnSearchClose = findViewById(R.id.btnSearchClose);
        txtEmpty = (TextView) findViewById(R.id.txtEmpty);

        listener = this;
        adapter = new FileAdapter(this, listener);
        listView.setAdapter(adapter);

        bindActions();
        bindSearch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUi();
        if (searchMode) {
            // returning from a preview: keep the current search results
            return;
        }
        if (hasStorageAccess()) {
            File appDir = appPrivateDir();
            boolean inPrivate = currentDir != null && appDir != null
                    && currentDir.getAbsolutePath().startsWith(appDir.getAbsolutePath());
            if (currentDir == null || inPrivate) {
                openDir(defaultDir());
            } else if (needsRefresh) {
                // only rescan when something actually changed (compress/extract/delete/rename)
                needsRefresh = false;
                openDir(currentDir);
            }
        } else if (currentDir == null) {
            openDir(appPrivateDir());
        }
    }

    private void bindActions() {
        findViewById(R.id.btnSort).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortMenu();
            }
        });
        findViewById(R.id.btnMore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu();
            }
        });
        findViewById(R.id.btnUp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUp();
            }
        });
        txtPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPathMenu();
            }
        });
        findViewById(R.id.btnSelectAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapter.getSelected().size() >= adapter.getFiles().size()) {
                    adapter.setSelected(new java.util.HashSet<String>());
                    exitSelection();
                } else {
                    adapter.selectAll();
                    updateSelectionUi();
                }
            }
        });
        findViewById(R.id.btnSelClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSelection();
            }
        });
        findViewById(R.id.actCompress).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCompress();
            }
        });
        findViewById(R.id.actExtract).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExtractFromSelection();
            }
        });
        findViewById(R.id.actDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDelete();
            }
        });
        findViewById(R.id.actMore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectionMenu();
            }
        });
        findViewById(R.id.btnGrant).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStorageAccess();
            }
        });
        btnAppSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppDetails();
            }
        });
    }

    // ------------------------------------------------------------------ navigation

    private File defaultDir() {
        File root = Environment.getExternalStorageDirectory();
        File download = new File(root, "Download");
        return download.isDirectory() ? download : root;
    }

    private File appPrivateDir() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        if (dir != null && !dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Lists the directory on a background thread. On big folders the old code did
     * listFiles() + one stat per comparison on the UI thread, which froze the list for
     * seconds; now the UI only renders pre-built rows (see {@link DirLoader}).
     */
    private void openDir(final File dir) {
        if (dir == null) return;
        if (!dir.isDirectory()) {
            U.toast(this, getString(R.string.err_permission));
            return;
        }
        saveScrollState();
        final int generation = ++dirGeneration;
        final int sort = sortMode;
        final boolean hidden = showHidden;
        currentDir = dir;
        exitSelectionInternal();

        List<FileItem> cached = cachedListing(dir, sort, hidden);
        if (cached != null) {
            applyDir(dir, cached);          // instant: rows were already built
            return;
        }
        showDirLoading(dir);

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                List<FileItem> loaded = null;
                boolean failed = false;
                try {
                    loaded = DirLoader.load(dir, hidden, sort, false, Environment.getExternalStorageDirectory());
                } catch (Exception e) {
                    failed = true;
                }
                final List<FileItem> items = loaded;
                final boolean error = failed;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != dirGeneration || isFinishing()) return;
                        if (error || items == null) {
                            showDirError(dir);
                            updatePermissionUi();
                            return;
                        }
                        rememberListing(dir, sort, hidden, items);
                        applyDir(dir, items);
                    }
                });
            }
        }, "ezip-list");
        worker.setDaemon(true);
        worker.start();
    }

    private void showDirLoading(File dir) {
        txtPath.setText(getString(R.string.loading_dir_title, displayPath(dir)));
        if (txtEmpty != null) txtEmpty.setText(R.string.loading_dir);
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        adapter.setItems(new ArrayList<FileItem>());
    }

    private void showDirError(File dir) {
        U.toast(this, getString(R.string.err_permission));
        txtPath.setText(displayPath(dir));
        if (txtEmpty != null) txtEmpty.setText(R.string.err_permission);
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
    }
    private void applyDir(File dir, List<FileItem> items) {
        adapter.setItems(items);
        txtPath.setText(displayPath(dir));
        boolean empty = items.isEmpty();
        if (txtEmpty != null) txtEmpty.setText(R.string.empty_dir);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) {
            if (pendingFocusPath != null) {
                String path = pendingFocusPath;
                pendingFocusPath = null;
                focusOn(path);
            } else {
                restoreScrollState(dir);
            }
        }
    }

    // ------------------------------------------------------------------ list state

    /** Remembers where the user was, anchored on the first visible row (not the index). */
    private void saveScrollState() {
        if (currentDir == null || adapter == null || listView == null) return;
        int index = listView.getFirstVisiblePosition();
        if (index < 0 || index >= adapter.getCount()) return;
        View first = listView.getChildAt(0);
        ScrollState state = new ScrollState();
        state.index = index;
        state.top = first == null ? 0 : first.getTop();
        state.anchorPath = adapter.getItems().get(index).file.getAbsolutePath();
        scrollStates.put(currentDir.getAbsolutePath(), state);
    }

    /** Restores the saved anchor, following it if the list changed (new files, deletions). */
    private void restoreScrollState(File dir) {
        ScrollState state = scrollStates.get(dir.getAbsolutePath());
        int index = 0;
        int top = 0;
        if (state != null) {
            int found = -1;
            List<FileItem> items = adapter.getItems();
            for (int i = 0; i < items.size(); i++) {
                if (state.anchorPath.equals(items.get(i).file.getAbsolutePath())) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                index = found;
                top = state.top;
            } else {
                // anchor disappeared (deleted / renamed): fall back to the old index, clamped
                index = Math.max(0, Math.min(state.index, adapter.getCount() - 1));
            }
        }
        // restore now, and once more after the data-change layout: notifyDataSetChanged()
        // runs on the next layout pass and can reset the position to 0
        final int targetIndex = index;
        final int targetTop = top;
        listView.setSelectionFromTop(targetIndex, targetTop);
        listView.post(new Runnable() {
            @Override
            public void run() {
                if (targetIndex > 0 && adapter.getCount() > targetIndex
                        && listView.getFirstVisiblePosition() == 0) {
                    listView.setSelectionFromTop(targetIndex, targetTop);
                }
            }
        });
    }

    private String listingKey(File dir, int sort, boolean hidden) {
        return dir.getAbsolutePath() + "|" + sort + "|" + (hidden ? "h" : "n");
    }

    private List<FileItem> cachedListing(File dir, int sort, boolean hidden) {
        String key = listingKey(dir, sort, hidden);
        CachedListing entry = listingCache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.time > LISTING_CACHE_MS) {
            listingCache.remove(key);
            return null;
        }
        return entry.items;
    }

    private void rememberListing(File dir, int sort, boolean hidden, List<FileItem> items) {
        // very large folders are not cached: the rows themselves would eat too much memory
        if (items == null || items.size() > 2000) return;
        if (listingCache.size() >= LISTING_CACHE_MAX) listingCache.clear();
        listingCache.put(listingKey(dir, sort, hidden), new CachedListing(items));
    }

    /** Any change on disk invalidates both the listing cache and the remembered positions. */
    private void invalidateListings() {
        listingCache.clear();
    }

    private String displayPath(File dir) {
        String path = dir.getAbsolutePath();
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.equals(root)) return getString(R.string.storage);
        if (path.startsWith(root + "/")) {
            return getString(R.string.storage) + path.substring(root.length());
        }
        return path;
    }

    private void goUp() {
        if (currentDir == null) return;
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (currentDir.getAbsolutePath().equals(root)) {
            U.toast(this, "已经在最上层了");
            return;
        }
        File parent = currentDir.getParentFile();
        if (parent == null) {
            U.toast(this, "已经在最上层了");
            return;
        }
        openDir(parent);
    }


    // ------------------------------------------------------------------ selection

    private void enterSelection() {
        selectionMode = true;
        adapter.setSelectionMode(true);
        toolbar.setVisibility(View.GONE);
        toolbarSelect.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
    }

    private void exitSelection() {
        exitSelectionInternal();
    }

    private void exitSelectionInternal() {
        selectionMode = false;
        if (adapter != null) {
            adapter.setSelectionMode(false);
            adapter.setSelected(new java.util.HashSet<String>());
        }
        toolbar.setVisibility(View.VISIBLE);
        toolbarSelect.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
    }

    private void updateSelectionUi() {
        int count = adapter.getSelected().size();
        txtSelCount.setText(getString(R.string.selected_count, count));
        if (count == 0) exitSelection();
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (FileItem item : adapter.getItems()) {
            if (adapter.isSelected(item.file)) out.add(item.file);
        }
        return out;
    }

    @Override
    public void onFileClick(File file) {
        if (selectionMode) {
            adapter.toggle(file);
            updateSelectionUi();
            return;
        }
        if (file.isDirectory()) {
            if (searchMode) exitSearchMode(false);
            openDir(file);
            return;
        }
        if (ArchiveKind.of(file.getName()).isArchive()) {
            showArchiveMenu(file);
            return;
        }
        PreviewActivity.open(this, file);
    }

    @Override
    public void onFileLongClick(File file) {
        if (!selectionMode) enterSelection();
        if (!adapter.isSelected(file)) adapter.toggle(file);
        updateSelectionUi();
    }

    @Override
    public void onBackPressed() {
        if (searchMode) {
            exitSearchMode(true);
            return;
        }
        if (selectionMode) {
            exitSelection();
            return;
        }
        if (currentDir != null) {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (!currentDir.getAbsolutePath().equals(root) && currentDir.getParentFile() != null) {
                openDir(currentDir.getParentFile());
                return;
            }
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ menus

    private void showSortMenu() {
        String[] items = {getString(R.string.sort_name), getString(R.string.sort_size),
                getString(R.string.sort_date)};
        Dialogs.menu(this, getString(R.string.sort), items, new Dialogs.OnMenuItem() {
            @Override
            public void onPick(int index) {
                sortMode = index;
                if (currentDir != null) openDir(currentDir);
            }
        });
    }

    private void showMoreMenu() {
        final String[] items = {
                getString(R.string.new_folder),
                getString(R.string.sort),
                showHidden ? getString(R.string.hide_hidden) : getString(R.string.show_hidden),
                getString(R.string.refresh),
                hasStorageAccess() ? "权限设置" : "存储权限（未授予）",
                getString(R.string.privacy_title),
                getString(R.string.about)
        };
        Dialogs.menu(this, currentDir == null ? null : displayPath(currentDir), items, new Dialogs.OnMenuItem() {
            @Override
            public void onPick(int index) {
                switch (index) {
                    case 0:
                        createFolder();
                        break;
                    case 1:
                        showSortMenu();
                        break;
                    case 2:
                        showHidden = !showHidden;
                        invalidateListings();
                        if (currentDir != null) openDir(currentDir);
                        break;
                    case 3:
                        invalidateListings();
                        if (currentDir != null) openDir(currentDir);
                        break;
                    case 4:
                        updatePermissionUi();
                        requestStorageAccess();
                        break;
                    case 5:
                        showPrivacy();
                        break;
                    default:
                        showAbout();
                        break;
                }
            }
        });
    }

    private void showSelectionMenu() {
        final String[] items = {getString(R.string.rename), getString(R.string.select_all)};
        Dialogs.menu(this, getString(R.string.more), items, new Dialogs.OnMenuItem() {
            @Override
            public void onPick(int index) {
                if (index == 0) {
                    List<File> chosen = selectedFiles();
                    if (chosen.size() != 1) {
                        U.toast(MainActivity.this, "请选择一个文件");
                        return;
                    }
                    rename(chosen.get(0));
                } else {
                    adapter.selectAll();
                    updateSelectionUi();
                }
            }
        });
    }

    private void showArchiveMenu(final File archive) {
        String[] items = {getString(R.string.view_content), getString(R.string.extract_here),
                getString(R.string.extract_to)};
        Dialogs.menu(this, archive.getName(), items, new Dialogs.OnMenuItem() {
            @Override
            public void onPick(int index) {
                if (index == 0) {
                    ArchiveActivity.open(MainActivity.this, archive);
                } else if (index == 1) {
                    File parent = archive.getParentFile() == null ? currentDir : archive.getParentFile();
                    Dialogs.extract(MainActivity.this, archive, parent, extractCallback(archive));
                } else {
                    Dialogs.extract(MainActivity.this, archive, uniqueTarget(archive), extractCallback(archive));
                }
            }
        });
    }

    private File uniqueTarget(File archive) {
        File parent = archive.getParentFile() == null ? currentDir : archive.getParentFile();
        String base = FileX.stripExtension(archive.getName());
        if (base.isEmpty()) base = "解压结果";
        return FileX.uniqueDir(parent, base);
    }

    private void showPathMenu() {
        final List<File> targets = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        targets.add(root);
        labels.add(getString(R.string.storage));
        addIfExists(targets, labels, new File(root, "Download"), "下载");
        addIfExists(targets, labels, new File(root, "DCIM"), "相机");
        addIfExists(targets, labels, new File(root, "Pictures"), "图片");
        addIfExists(targets, labels, new File(root, "Documents"), "文档");
        addIfExists(targets, labels, new File(root, "Music"), "音乐");
        addIfExists(targets, labels, new File(root, "Movies"), "视频");
        targets.add(appPrivateDir());
        labels.add("应用目录");

        Dialogs.menu(this, null, labels.toArray(new String[0]), new Dialogs.OnMenuItem() {
            @Override
            public void onPick(int index) {
                openDir(targets.get(index));
            }
        });
    }

    private void addIfExists(List<File> targets, List<String> labels, File dir, String label) {
        if (dir.isDirectory()) {
            targets.add(dir);
            labels.add(label);
        }
    }

    private void showPrivacy() {
        Dialog dialog = U.newDialog(this, R.layout.dialog_progress);
        TextView title = (TextView) dialog.findViewById(R.id.txtTitle);
        TextView msg = (TextView) dialog.findViewById(R.id.txtMsg);
        TextView detail = (TextView) dialog.findViewById(R.id.txtDetail);
        View progress = dialog.findViewById(R.id.progress);
        TextView cancel = (TextView) dialog.findViewById(R.id.btnCancel);
        progress.setVisibility(View.GONE);
        detail.setVisibility(View.GONE);
        title.setText(R.string.privacy_title);
        msg.setMaxLines(40);
        msg.setText(R.string.privacy_msg);
        cancel.setText(R.string.confirm);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(true);
        dialog.show();
    }
    private void showAbout() {
        Dialog dialog = U.newDialog(this, R.layout.dialog_progress);
        TextView title = (TextView) dialog.findViewById(R.id.txtTitle);
        TextView msg = (TextView) dialog.findViewById(R.id.txtMsg);
        TextView detail = (TextView) dialog.findViewById(R.id.txtDetail);
        View progress = dialog.findViewById(R.id.progress);
        TextView cancel = (TextView) dialog.findViewById(R.id.btnCancel);
        progress.setVisibility(View.GONE);
        title.setText(R.string.app_name);
        msg.setMaxLines(30);
        msg.setText(R.string.about_msg);
        detail.setVisibility(View.GONE);
        cancel.setText(R.string.confirm);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(true);
        dialog.show();
    }

    // ------------------------------------------------------------------ permission

    /** targetSdk the APK was built with; the compat build uses 29 (legacy storage). */
    private int targetSdkVersion() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.applicationInfo.targetSdkVersion;
        } catch (Exception e) {
            return Build.VERSION.SDK_INT;
        }
    }

    private boolean isLegacyStorageBuild() {
        return targetSdkVersion() <= Build.VERSION_CODES.Q;
    }

    private boolean hasStorageAccess() {
        if (isLegacyStorageBuild()) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        // targetSdk >= 30 running on Android 10 or lower: scoped storage is forced,
        // MANAGE_EXTERNAL_STORAGE does not exist yet, so full browsing is impossible.
        return false;
    }

    /** Refreshes the permission card: what is missing, why, and which button to tap. */
    private void updatePermissionUi() {
        boolean granted = hasStorageAccess();
        permCard.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (granted || txtPermStatus == null) return;

        final StringBuilder status = new StringBuilder();
        status.append("系统 Android ").append(Build.VERSION.RELEASE)
                .append("（API ").append(Build.VERSION.SDK_INT).append("）")
                .append(" · 应用目标 SDK ").append(targetSdkVersion());

        if (isLegacyStorageBuild()) {
            txtPermDesc.setText(R.string.perm_desc_legacy);
            btnGrant.setText(R.string.perm_btn_legacy);
            btnGrant.setEnabled(true);
            btnGrant.setAlpha(1f);
            status.append("\n模式：兼容版（旧版存储），需要「存储」权限");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            txtPermDesc.setText(R.string.perm_desc);
            btnGrant.setText(R.string.perm_btn_manage);
            btnGrant.setEnabled(true);
            btnGrant.setAlpha(1f);
            status.append("\n模式：所有文件访问，需要在系统设置里手动开启");
            status.append('\n').append(getString(R.string.perm_hint_manage));
        } else {
            txtPermDesc.setText(R.string.perm_desc_too_old);
            btnGrant.setText(R.string.perm_btn_too_old);
            btnGrant.setEnabled(false);
            btnGrant.setAlpha(0.5f);
            status.append("\n模式：系统版本过低，无法获得全部文件访问，请换兼容版 APK");
        }
        probeStorageRoot(status);
    }

    /**
     * Listing the storage root is a real syscall storm on big volumes, so the diagnostic
     * line is filled in from a background thread instead of blocking the UI.
     */
    private void probeStorageRoot(final StringBuilder status) {
        final String base = status.toString();
        txtPermStatus.setText(base + "\n存储根目录：检测中…");
        Thread probe = new Thread(new Runnable() {
            @Override
            public void run() {
                String state;
                try {
                    File root = Environment.getExternalStorageDirectory();
                    File[] entries = root == null ? null : root.listFiles();
                    state = entries == null ? "不可读" : ("可读，" + entries.length + " 项");
                } catch (Throwable t) {
                    state = "读取失败";
                }
                final String rootState = state;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (txtPermStatus == null || permCard.getVisibility() != View.VISIBLE) return;
                        txtPermStatus.setText(base + "\n存储根目录：" + rootState);
                    }
                });
            }
        }, "ezip-probe");
        probe.setDaemon(true);
        probe.start();
    }
    private void requestStorageAccess() {
        if (isLegacyStorageBuild()) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISSION);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ROMs differ: try the per-app page, then the global list, then app details
            if (openSettings(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())))) {
                return;
            }
            if (openSettings(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) {
                return;
            }
        }
        openAppDetails();
    }

    private boolean openSettings(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!openSettings(intent)) {
            U.toast(this, "请到「设置 → 应用 → EZip」中手动授予存储权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSION) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        updatePermissionUi();
        if (granted) {
            openDir(defaultDir());
        } else {
            U.toast(this, getString(R.string.err_permission));
        }
    }

    // ------------------------------------------------------------------ search

    private void bindSearch() {
        findViewById(R.id.btnSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterSearchMode();
            }
        });
        btnSearchClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSearchMode(true);
            }
        });
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearchWatcher) return;
                scheduleSearch(s == null ? "" : s.toString());
            }
        });
        editSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String query = v.getText().toString().trim();
                    if (query.length() > 0) runSearch(query);
                    return true;
                }
                return false;
            }
        });
    }

    private void enterSearchMode() {
        if (currentDir == null) return;
        saveScrollState();
        searchMode = true;
        searchBar.setVisibility(View.VISIBLE);
        txtPath.setText(getString(R.string.search_scope, displayPath(currentDir)));
        editSearch.requestFocus();
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) {
            // some ROMs refuse to open the keyboard programmatically
        }
    }

    private void exitSearchMode(boolean restoreList) {
        searchMode = false;
        searchGeneration++;
        searchHandler.removeCallbacksAndMessages(null);
        pendingSearch = null;
        suppressSearchWatcher = true;
        editSearch.setText("");
        suppressSearchWatcher = false;
        searchBar.setVisibility(View.GONE);
        hideKeyboard();
        if (restoreList && currentDir != null) openDir(currentDir);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        } catch (Exception ignored) {
            // nothing to hide
        }
    }

    private void scheduleSearch(String text) {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        final String query = text == null ? "" : text.trim();
        if (query.isEmpty()) {
            searchGeneration++;
            adapter.setItems(new ArrayList<FileItem>());
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            if (currentDir != null) {
                txtPath.setText(getString(R.string.search_scope, displayPath(currentDir)));
            }
            return;
        }
        pendingSearch = new Runnable() {
            @Override
            public void run() {
                pendingSearch = null;
                runSearch(query);
            }
        };
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void runSearch(final String query) {
        final File root = currentDir;
        if (root == null) return;
        final int generation = ++searchGeneration;
        pendingFocusPath = null;
        txtPath.setText(getString(R.string.search_scope, displayPath(root)));

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                List<File> results;
                try {
                    results = FileSearch.search(root, query, showHidden,
                            SEARCH_MAX_RESULTS, SEARCH_MAX_DIRS, new FileSearch.Callback() {
                            private long lastPost;

                            @Override
                            public boolean isCancelled() {
                                return generation != searchGeneration;
                            }

                            @Override
                            public void onProgress(final List<File> snapshot, int scannedDirs) {
                                long now = System.currentTimeMillis();
                                if (now - lastPost < 200) return;
                                lastPost = now;
                                final List<FileItem> items = toItems(snapshot);
                                searchHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (generation != searchGeneration) return;
                                        applyResults(items, false);
                                    }
                                });
                            }
                        });
                } catch (Throwable t) {
                    results = new ArrayList<File>();
                }

                if (generation != searchGeneration) return;
                // build the rows here (stat per hit) so the UI thread never touches the filesystem
                final List<FileItem> finalItems = new ArrayList<>(results.size());
                File storageRoot = Environment.getExternalStorageDirectory();
                for (File hit : results) {
                    finalItems.add(DirLoader.item(hit, true, storageRoot));
                }
                searchHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != searchGeneration) return;
                        applyResults(finalItems, true);
                    }
                });
            }
        }, "ezip-search");
        worker.setDaemon(true);
        worker.start();
    }

    /** Converts filesystem entries to render-ready rows (stat calls happen here, off the UI thread). */
    private List<FileItem> toItems(List<File> files) {
        File storageRoot = Environment.getExternalStorageDirectory();
        List<FileItem> items = new ArrayList<>(files.size());
        for (File file : files) {
            items.add(DirLoader.item(file, true, storageRoot));
        }
        return items;
    }
    private void applyResults(List<FileItem> results, boolean finished) {
        adapter.setItems(results);
        if (results.isEmpty()) {
            if (finished) {
                txtEmpty.setText(R.string.search_empty);
                txtPath.setText(R.string.search_empty);
                emptyView.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
            } else {
                txtPath.setText(getString(R.string.search_searching, 0));
                emptyView.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
            }
            return;
        }
        emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        if (!finished) {
            txtPath.setText(getString(R.string.search_searching, results.size()));
        } else if (results.size() >= SEARCH_MAX_RESULTS) {
            txtPath.setText(getString(R.string.search_limit, results.size()));
        } else {
            txtPath.setText(getString(R.string.search_found, results.size()));
        }
    }

    /** After compress/extract/delete: refresh the folder, or re-run the active search. */
    private void refreshAfterWork() {
        needsRefresh = true;
        invalidateListings();
        if (searchMode) {
            String query = editSearch.getText().toString().trim();
            if (query.length() > 0) {
                runSearch(query);
                return;
            }
            exitSearchMode(true);
            return;
        }
        if (currentDir != null) openDir(currentDir);
    }
    // ------------------------------------------------------------------ actions

    private void createFolder() {
        if (currentDir == null) return;
        Dialogs.input(this, getString(R.string.new_folder_title), getString(R.string.name_hint), "",
                new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        String name = U.safeName(text);
                        if (name.length() == 0) {
                            U.toast(MainActivity.this, getString(R.string.err_empty_name));
                            return;
                        }
                        File dir = new File(currentDir, name);
                        if (dir.exists()) {
                            U.toast(MainActivity.this, "同名文件已存在");
                            return;
                        }
                        if (!dir.mkdirs()) {
                            U.toast(MainActivity.this, "创建失败");
                            return;
                        }
                        openDir(currentDir);
                    }
                });
    }

    private void rename(final File file) {
        Dialogs.input(this, getString(R.string.rename_title), getString(R.string.name_hint),
                file.getName(), new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        String name = U.safeName(text);
                        if (name.length() == 0) {
                            U.toast(MainActivity.this, getString(R.string.err_empty_name));
                            return;
                        }
                        File target = new File(file.getParentFile(), name);
                        if (target.exists()) {
                            U.toast(MainActivity.this, "同名文件已存在");
                            return;
                        }
                        if (!file.renameTo(target)) {
                            U.toast(MainActivity.this, "重命名失败");
                            return;
                        }
                        exitSelection();
                        if (currentDir != null) openDir(currentDir);
                    }
                });
    }

    private void startCompress() {
        final List<File> chosen = selectedFiles();
        if (chosen.isEmpty()) {
            U.toast(this, getString(R.string.err_no_selection));
            return;
        }
        if (currentDir == null) return;
        Dialogs.compress(this, chosen, currentDir, new Dialogs.OnCompress() {
            @Override
            public void onStart(final File outFile, final int format, final int level, final String password) {
                TaskRunner.start(MainActivity.this,
                        getString(R.string.compress), outFile.getName(),
                        new TaskRunner.Work() {
                            @Override
                            public void run(ProgressSink sink, CancelToken token) throws Exception {
                                Compressor.compress(chosen, outFile, format, level, password, sink, token);
                            }
                        },
                        new TaskRunner.Done() {
                            @Override
                            public void onFinished(Exception error, boolean cancelled) {
                                finishWork(error, cancelled, outFile, outFile, getString(R.string.done_compress));
                            }
                        });
            }
        });
    }

    private void startExtractFromSelection() {
        List<File> chosen = selectedFiles();
        if (chosen.size() != 1) {
            U.toast(this, getString(R.string.err_single_archive));
            return;
        }
        File archive = chosen.get(0);
        if (!ArchiveKind.of(archive.getName()).isArchive()) {
            U.toast(this, "请选择一个压缩包");
            return;
        }
        Dialogs.extract(this, archive, uniqueTarget(archive), extractCallback(archive));
    }

    private Dialogs.OnExtract extractCallback(final File archive) {
        return new Dialogs.OnExtract() {
            @Override
            public void onStart(final File targetDir, final String password) {
                File[] existing = targetDir.listFiles();
                if (targetDir.isDirectory() && existing != null && existing.length > 0) {
                    // extracting into a non empty folder overwrites same-named files: make it explicit
                    Dialogs.confirm(MainActivity.this, getString(R.string.extract_title),
                            "目标文件夹非空，同名文件将被覆盖。", getString(R.string.confirm),
                            new Runnable() {
                                @Override
                                public void run() {
                                    runExtract(archive, targetDir, password);
                                }
                            });
                } else {
                    runExtract(archive, targetDir, password);
                }
            }
        };
    }

    private void runExtract(final File archive, final File targetDir, final String password) {
        TaskRunner.start(MainActivity.this,
                getString(R.string.extract), archive.getName(),
                new TaskRunner.Work() {
                    @Override
                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                        FileX.ensureDir(targetDir);
                        ArchiveService.extract(archive, targetDir, password, sink, token);
                    }
                },
                new TaskRunner.Done() {
                    @Override
                    public void onFinished(Exception error, boolean cancelled) {
                        finishWork(error, cancelled, null, targetDir, getString(R.string.done_extract));
                        if (error == null && !cancelled && currentDir != null) {
                            File parent = targetDir.getParentFile();
                            if (parent != null && parent.getAbsolutePath().equals(currentDir.getAbsolutePath())) {
                                openDir(targetDir);
                                return;
                            }
                            openDir(currentDir);
                        }
                    }
                });
    }

    private void startDelete() {
        final List<File> chosen = selectedFiles();
        if (chosen.isEmpty()) {
            U.toast(this, getString(R.string.err_no_selection));
            return;
        }
        Dialogs.confirm(this, getString(R.string.delete_confirm_title),
                getString(R.string.delete_confirm_msg, chosen.size()),
                getString(R.string.delete), new Runnable() {
                    @Override
                    public void run() {
                        TaskRunner.start(MainActivity.this, getString(R.string.delete), "",
                                new TaskRunner.Work() {
                                    @Override
                                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                                        for (int i = 0; i < chosen.size(); i++) {
                                            token.throwIfCancelled();
                                            FileX.deleteRecursive(chosen.get(i));
                                        }
                                    }
                                },
                                new TaskRunner.Done() {
                                    @Override
                                    public void onFinished(Exception error, boolean cancelled) {
                                        if (error != null) {
                                            U.toast(MainActivity.this, ArchiveService.describe(error));
                                        } else {
                                            U.toast(MainActivity.this, getString(R.string.done_delete));
                                        }
                                        exitSelection();
                                        refreshAfterWork();
                                    }
                                });
                    }
                });
    }

    private void finishWork(Exception error, boolean cancelled, File cleanupOnFail, File result, String successText) {
        exitSelection();
        if (cancelled) {
            if (cleanupOnFail != null) FileX.deleteRecursive(cleanupOnFail);
            U.toast(this, "已取消");
            refreshAfterWork();
            return;
        }
        if (error != null) {
            if (cleanupOnFail != null) FileX.deleteRecursive(cleanupOnFail);
            U.toast(this, error instanceof OperationCancelledException ? "已取消"
                    : ArchiveService.describe(error));
            refreshAfterWork();
            return;
        }
        String message = successText;
        if (result != null) {
            String where = result.isDirectory() ? U.folderLabel(result)
                    : result.getName() + "  ·  " + U.folderLabel(result.getParentFile());
            message = successText + "\n" + where;
        }
        U.toast(this, message);
        if (result != null) pendingFocusPath = result.getAbsolutePath();
        refreshAfterWork();
    }

    /** Scrolls the list to a freshly created archive/folder and flashes it briefly. */
    private void focusOn(final String path) {
        if (path == null || adapter == null) return;
        java.util.List<FileItem> items = adapter.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (path.equals(items.get(i).file.getAbsolutePath())) {
                adapter.setHighlight(path);
                listView.smoothScrollToPosition(i);
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (adapter != null) adapter.setHighlight(null);
                    }
                }, 2500);
                return;
            }
        }
    }
}
