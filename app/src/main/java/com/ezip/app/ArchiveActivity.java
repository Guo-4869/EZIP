package com.ezip.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.ezip.app.archive.ArchiveEntryInfo;
import com.ezip.app.archive.ArchiveService;
import com.ezip.app.archive.CancelToken;
import com.ezip.app.archive.ProgressSink;
import com.ezip.app.ui.Dialogs;
import com.ezip.app.ui.EntryAdapter;
import com.ezip.app.ui.Kind;
import com.ezip.app.ui.TaskRunner;
import com.ezip.app.ui.U;
import com.ezip.app.util.FileX;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what is inside an archive; tapping an entry extracts it into cache for preview.
 * Encrypted archives are detected up front and the password prompt resumes the action
 * the user was trying to do (preview one entry, or extract everything).
 */
public class ArchiveActivity extends Activity implements EntryAdapter.Listener {

    private static final String EXTRA_PATH = "path";

    public static void open(Activity activity, File archive) {
        Intent intent = new Intent(activity, ArchiveActivity.class);
        intent.putExtra(EXTRA_PATH, archive.getAbsolutePath());
        activity.startActivity(intent);
    }

    private File archive;
    private String password = "";
    private boolean encrypted;
    private ArchiveEntryInfo pendingEntry;
    private boolean pendingExtractAll;
    private TextView txtInfo;
    private EntryAdapter adapter;
    private final List<ArchiveEntryInfo> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        archive = path == null ? null : new File(path);
        if (archive == null || !archive.isFile()) {
            U.toast(this, "压缩包不存在");
            finish();
            return;
        }

        TextView txtTitle = (TextView) findViewById(R.id.txtTitle);
        txtInfo = (TextView) findViewById(R.id.txtInfo);
        txtTitle.setText(archive.getName());

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnExtractAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractAll();
            }
        });

        adapter = new EntryAdapter(this, this);
        ((ListView) findViewById(R.id.list)).setAdapter(adapter);
        loadEntries();
    }

    // ------------------------------------------------------------------ listing

    private void loadEntries() {
        TaskRunner.start(this, getString(R.string.loading), archive.getName(),
                new TaskRunner.Work() {
                    @Override
                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                        List<ArchiveEntryInfo> list = ArchiveService.list(archive, password);
                        entries.clear();
                        entries.addAll(list);
                        // headers tell us about encryption without needing the password
                        if (!encrypted) encrypted = ArchiveService.isEncrypted(archive);
                    }
                },
                new TaskRunner.Done() {
                    @Override
                    public void onFinished(Exception error, boolean cancelled) {
                        if (error != null) {
                            if (password.isEmpty() && ArchiveService.isPasswordError(error)) {
                                askPassword("压缩包已加密，请输入密码");
                                return;
                            }
                            U.toast(ArchiveActivity.this, ArchiveService.describe(error));
                            finish();
                            return;
                        }
                        adapter.setEntries(entries);
                        updateInfo();
                    }
                });
    }

    private void updateInfo() {
        String info = entries.size() + " 个文件 · " + FileX.formatSize(archive.length());
        if (encrypted) {
            info += password.isEmpty() ? "  ·  已加密（点击文件时输入密码）" : "  ·  已加密";
        }
        txtInfo.setText(info);
    }

    // ------------------------------------------------------------------ password

    /**
     * Prompts for the archive password, then resumes whatever the user wanted:
     * preview the pending entry, extract everything, or just reload the listing.
     */
    private void askPassword(String title) {
        Dialogs.input(this, title, getString(R.string.extract_password_hint), "",
                new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        if (text == null || text.trim().isEmpty()) {
                            pendingEntry = null;
                            pendingExtractAll = false;
                            return;
                        }
                        password = text.trim();
                        updateInfo();

                        ArchiveEntryInfo entry = pendingEntry;
                        boolean extractAll = pendingExtractAll;
                        pendingEntry = null;
                        pendingExtractAll = false;

                        if (entry != null) {
                            extractAndPreview(entry);
                        } else if (extractAll) {
                            ArchiveActivity.this.extractAll();
                        } else {
                            loadEntries();
                        }
                    }
                });
    }

    // ------------------------------------------------------------------ entry preview

    @Override
    public void onEntryClick(final ArchiveEntryInfo entry) {
        if (entry.directory) return;
        if (encrypted && password.isEmpty()) {
            pendingEntry = entry;
            pendingExtractAll = false;
            askPassword("压缩包已加密，输入密码后可预览");
            return;
        }
        extractAndPreview(entry);
    }

    private void extractAndPreview(final ArchiveEntryInfo entry) {
        final File out = cacheFileFor(entry.name);
        TaskRunner.start(this, getString(R.string.loading), baseName(entry.name),
                new TaskRunner.Work() {
                    @Override
                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                        ArchiveService.extractEntry(archive, entry.name, password, out);
                    }
                },
                new TaskRunner.Done() {
                    @Override
                    public void onFinished(Exception error, boolean cancelled) {
                        if (error != null) {
                            FileX.deleteRecursive(out);
                            if (ArchiveService.isPasswordError(error)) {
                                pendingEntry = entry;
                                pendingExtractAll = false;
                                askPassword(password.isEmpty()
                                        ? "压缩包已加密，输入密码后可预览"
                                        : "密码错误，请重新输入");
                                return;
                            }
                            U.toast(ArchiveActivity.this, ArchiveService.describe(error));
                            return;
                        }
                        Kind kind = Kind.of(out.getName());
                        if (kind == Kind.OTHER) {
                            U.openWith(ArchiveActivity.this, out);
                        } else {
                            PreviewActivity.open(ArchiveActivity.this, out);
                        }
                    }
                });
    }

    private File cacheFileFor(String entryName) {
        File cacheDir = new File(getCacheDir(), "preview");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            U.toast(this, "无法写入缓存目录");
        }
        String base = baseName(entryName);
        return new File(cacheDir, Integer.toHexString(entryName.hashCode()) + "_" + U.safeName(base));
    }

    private String baseName(String entryName) {
        int slash = entryName.lastIndexOf('/');
        return slash >= 0 ? entryName.substring(slash + 1) : entryName;
    }

    // ------------------------------------------------------------------ extract all

    private void extractAll() {
        if (encrypted && password.isEmpty()) {
            pendingEntry = null;
            pendingExtractAll = true;
            askPassword("压缩包已加密，输入密码后解压");
            return;
        }
        File parent = archive.getParentFile();
        if (parent == null) parent = getExternalFilesDir(null);
        String base = FileX.stripExtension(archive.getName());
        final File target = FileX.uniqueDir(parent, base.isEmpty() ? "解压结果" : base);

        TaskRunner.start(this, getString(R.string.extract), archive.getName(),
                new TaskRunner.Work() {
                    @Override
                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                        FileX.ensureDir(target);
                        ArchiveService.extract(archive, target, password, sink, token);
                    }
                },
                new TaskRunner.Done() {
                    @Override
                    public void onFinished(Exception error, boolean cancelled) {
                        if (cancelled) {
                            U.toast(ArchiveActivity.this, "已取消");
                            return;
                        }
                        if (error != null) {
                            if (ArchiveService.isPasswordError(error)) {
                                pendingExtractAll = true;
                                askPassword(password.isEmpty()
                                        ? "压缩包已加密，输入密码后解压"
                                        : "密码错误，请重新输入");
                                return;
                            }
                            U.toast(ArchiveActivity.this, ArchiveService.describe(error));
                            return;
                        }
                        U.toast(ArchiveActivity.this, "已解压到 " + U.folderLabel(target));
                        finish();
                    }
                });
    }
}
