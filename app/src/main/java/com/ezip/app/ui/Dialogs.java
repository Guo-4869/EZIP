package com.ezip.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.ezip.app.R;
import com.ezip.app.archive.Compressor;
import com.ezip.app.util.FileX;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** All EZip dialogs (menu sheet, inputs, compress, extract, folder picker). */
public final class Dialogs {

    public interface OnMenuItem {
        void onPick(int index);
    }

    public interface OnText {
        void onText(String text);
    }

    public interface OnCompress {
        void onStart(File outFile, int format, int level, String password);
    }

    public interface OnExtract {
        void onStart(File targetDir, String password);
    }

    public interface OnFolder {
        void onPick(File folder);
    }

    private Dialogs() {
    }

    // ------------------------------------------------------------------ menu sheet

    public static void menu(final Activity activity, String title, final String[] items, final OnMenuItem callback) {
        final Dialog dialog = U.newSheet(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(U.topRounded(activity, U.color(activity, R.color.surface), 18));
        int topPad = U.dp(activity, 10);
        root.setPadding(0, topPad, 0, U.dp(activity, 10));

        if (title != null && title.length() > 0) {
            TextView header = U.text(activity, title, 12, U.color(activity, R.color.text2),
                    20, 6);
            header.setSingleLine(true);
            header.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            root.addView(header, matchWrap());
        }

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView row = U.text(activity, items[i], 15, U.color(activity, R.color.text), 22, 14);
            row.setBackgroundResource(ripple(activity));
            if (items[i].startsWith("! ")) {
                row.setText(items[i].substring(2));
                row.setTextColor(U.color(activity, R.color.danger));
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    callback.onPick(index);
                }
            });
            root.addView(row, matchWrap());
        }

        TextView cancel = U.text(activity, activity.getString(R.string.cancel), 15,
                U.color(activity, R.color.text2), 22, 14);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackgroundResource(ripple(activity));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        View divider = new View(activity);
        divider.setBackgroundColor(U.color(activity, R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, U.dp(activity, 1));
        lp.topMargin = U.dp(activity, 8);
        lp.bottomMargin = U.dp(activity, 4);
        root.addView(divider, lp);
        root.addView(cancel, matchWrap());

        dialog.setContentView(root);
        try {
            dialog.show();
        } catch (WindowManager.BadTokenException ignored) {
            return;
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ------------------------------------------------------------------ simple dialogs

    public static void confirm(final Activity activity, String title, String message,
                               String positive, final Runnable onYes) {
        new android.app.AlertDialog.Builder(activity, R.style.DialogTheme)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        onYes.run();
                    }
                })
                .show();
    }

    public static void input(final Activity activity, String title, String hint, String preset,
                             final OnText onSubmit) {
        LinearLayout root = column(activity, 20);
        root.addView(U.text(activity, title, 17, U.color(activity, R.color.text), 0, 0));
        final EditText input = new EditText(activity);
        input.setTextSize(15);
        input.setTextColor(U.color(activity, R.color.text));
        input.setHintTextColor(U.color(activity, R.color.text3));
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(U.dp(activity, 12), U.dp(activity, 10), U.dp(activity, 12), U.dp(activity, 10));
        if (preset != null) {
            input.setText(preset);
            input.setSelection(preset.length());
        }
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.topMargin = U.dp(activity, 14);
        root.addView(input, inputLp);

        final Dialog dialog = U.newDialog(activity, root, false);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsLp = matchWrap();
        buttonsLp.topMargin = U.dp(activity, 20);
        root.addView(buttons, buttonsLp);

        TextView cancel = button(activity, activity.getString(R.string.cancel), false);
        TextView ok = button(activity, activity.getString(R.string.confirm), true);

        LinearLayout.LayoutParams halfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        buttons.addView(cancel, halfLp);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        okLp.leftMargin = U.dp(activity, 10);
        buttons.addView(ok, okLp);

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                dialog.dismiss();
                onSubmit.onText(text);
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                input.requestFocus();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ compress

    public static void compress(final Activity activity, List<File> items, final File targetDir,
                                final OnCompress callback) {
        final Dialog dialog = U.newDialog(activity, R.layout.dialog_compress);
        TextView summary = (TextView) dialog.findViewById(R.id.txtSummary);
        final EditText editName = (EditText) dialog.findViewById(R.id.editName);
        final Spinner spFormat = (Spinner) dialog.findViewById(R.id.spFormat);
        final RadioGroup radioLevel = (RadioGroup) dialog.findViewById(R.id.radioLevel);
        final EditText editPassword = (EditText) dialog.findViewById(R.id.editPassword);

        summary.setText(items.size() + " 项\n保存到 " + U.folderLabel(targetDir));
        String suggested = Compressor.suggestName(items, Compressor.FORMAT_ZIP);
        editName.setText(suggested);
        editName.setSelection(suggested.length());

        final int[] formats = {
                Compressor.FORMAT_ZIP, Compressor.FORMAT_7Z, Compressor.FORMAT_TAR,
                Compressor.FORMAT_TAR_GZ, Compressor.FORMAT_TAR_BZ2, Compressor.FORMAT_TAR_XZ
        };
        String[] labels = {
                activity.getString(R.string.compress_format_zip),
                activity.getString(R.string.compress_format_7z),
                activity.getString(R.string.compress_format_tar),
                activity.getString(R.string.compress_format_tar_gz),
                activity.getString(R.string.compress_format_tar_bz2),
                activity.getString(R.string.compress_format_tar_xz)
        };
        spFormat.setAdapter(new FormatAdapter(activity, labels));
        spFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int format = formats[Math.max(0, Math.min(position, formats.length - 1))];
                applyFormat(activity, format, editName, editPassword);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // keep the current format
            }
        });

        dialog.findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.findViewById(R.id.btnStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = spFormat.getSelectedItemPosition();
                int format = formats[Math.max(0, Math.min(position, formats.length - 1))];
                String ext = Compressor.extensionFor(format);
                String typed = U.safeName(editName.getText().toString());
                if (typed.length() == 0) {
                    U.toast(activity, activity.getString(R.string.err_empty_name));
                    return;
                }
                String fileName = typed.toLowerCase(Locale.US).endsWith(ext) ? typed : typed + ext;
                if (fileName.length() <= ext.length()) {
                    U.toast(activity, activity.getString(R.string.err_empty_name));
                    return;
                }
                int level;
                int levelId = radioLevel.getCheckedRadioButtonId();
                if (levelId == R.id.lvlStore) {
                    level = Compressor.LEVEL_STORE;
                } else if (levelId == R.id.lvlMax) {
                    level = Compressor.LEVEL_MAX;
                } else {
                    level = Compressor.LEVEL_NORMAL;
                }
                File outFile = FileX.uniqueFile(targetDir, fileName);
                String password = Compressor.supportsPassword(format)
                        ? editPassword.getText().toString() : "";
                dialog.dismiss();
                callback.onStart(outFile, format, level, password);
            }
        });
        dialog.show();
    }

    /** Keeps the typed name's extension in sync with the chosen format and gates password entry. */
    private static void applyFormat(Activity activity, int format, EditText editName, EditText editPassword) {
        String current = editName.getText().toString().trim();
        String base = stripArchiveExtension(current);
        String updated = base + Compressor.extensionFor(format);
        if (!updated.equals(current)) {
            editName.setText(updated);
            editName.setSelection(updated.length());
        }
        boolean canEncrypt = Compressor.supportsPassword(format);
        editPassword.setEnabled(canEncrypt);
        editPassword.setAlpha(canEncrypt ? 1f : 0.45f);
        if (canEncrypt) {
            editPassword.setHint(R.string.password_hint);
        } else {
            editPassword.setText("");
            editPassword.setHint(R.string.tar_no_password);
        }
    }

    private static String stripArchiveExtension(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String ext : new String[]{".tar.gz", ".tar.bz2", ".tar.xz", ".zip", ".7z", ".tar"}) {
            if (lower.endsWith(ext) && name.length() > ext.length()) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name;
    }

    // ------------------------------------------------------------------ extract

    public static void extract(final Activity activity, final File archive, File defaultTarget,
                               final OnExtract callback) {
        final Dialog dialog = U.newDialog(activity, R.layout.dialog_extract);
        TextView summary = (TextView) dialog.findViewById(R.id.txtSummary);
        final EditText editTarget = (EditText) dialog.findViewById(R.id.editTarget);
        final EditText editPassword = (EditText) dialog.findViewById(R.id.editPassword);

        summary.setText(archive.getName() + "  ·  " + FileX.formatSize(archive.length()));
        final File initialTarget = defaultTarget;
        editTarget.setText(initialTarget.getAbsolutePath());
        editTarget.setSelection(editTarget.getText().length());

        dialog.findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.findViewById(R.id.btnStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String typed = editTarget.getText().toString().trim();
                if (typed.length() == 0) {
                    U.toast(activity, activity.getString(R.string.err_empty_name));
                    return;
                }
                File dir = new File(typed);
                if (!dir.isAbsolute()) {
                    dir = new File(initialTarget.getAbsolutePath() + "/" + typed);
                }
                String password = editPassword.getText().toString();
                dialog.dismiss();
                callback.onStart(dir, password);
            }
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ folder picker

    public static void pickFolder(final Activity activity, File start, final OnFolder callback) {
        final LinearLayout root = column(activity, 18);
        root.addView(U.text(activity, "选择文件夹", 17, U.color(activity, R.color.text), 0, 0));

        final TextView pathView = U.text(activity, "", 12, U.color(activity, R.color.text2), 0, 0);
        pathView.setSingleLine(true);
        pathView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams pathLp = matchWrap();
        pathLp.topMargin = U.dp(activity, 6);
        root.addView(pathView, pathLp);

        final ListView list = new ListView(activity);
        list.setDivider(null);
        list.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, U.dp(activity, 280));
        listLp.topMargin = U.dp(activity, 10);
        root.addView(list, listLp);

        final Dialog dialog = U.newDialog(activity, root, false);
        final File[] current = {start};

        final DirAdapter adapter = new DirAdapter(activity);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File dir = (File) adapter.getItem(position);
                current[0] = dir;
                refresh(activity, adapter, pathView, dir);
            }
        });

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsLp = matchWrap();
        buttonsLp.topMargin = U.dp(activity, 16);
        root.addView(buttons, buttonsLp);

        TextView cancel = button(activity, activity.getString(R.string.cancel), false);
        TextView ok = button(activity, "使用此文件夹", true);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        okLp.leftMargin = U.dp(activity, 10);
        buttons.addView(ok, okLp);

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File chosen = current[0];
                dialog.dismiss();
                callback.onPick(chosen);
            }
        });

        refresh(activity, adapter, pathView, start);
        dialog.show();
    }

    // ------------------------------------------------------------------ helpers

    private static void refresh(Activity activity, DirAdapter adapter, TextView pathView, File dir) {
        pathView.setText(dir.getAbsolutePath());
        File[] all = dir.listFiles();
        List<File> dirs = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                if (f.isDirectory() && f.canRead()) dirs.add(f);
            }
        }
        java.util.Collections.sort(dirs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        File parent = dir.getParentFile();
        if (parent != null && parent.canRead()) {
            File up = new File(dir, "..");
            dirs.add(0, up);
        }
        adapter.setDirs(dirs, dir);
    }

    private static TextView button(Activity activity, String text, boolean primary) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(primary ? 0xFFFFFFFF : U.color(activity, R.color.primary));
        tv.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_ghost);
        int ph = U.dp(activity, 18);
        int pv = U.dp(activity, 11);
        tv.setPadding(ph, pv, ph, pv);
        return tv;
    }

    private static int ripple(Activity activity) {
        TypedValue value = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private static LinearLayout column(Activity activity, int paddingDp) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(U.roundRect(activity, U.color(activity, R.color.surface), 18));
        int p = U.dp(activity, paddingDp);
        root.setPadding(p, p, p, p);
        return root;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Spinner adapter that inflates our own row layouts.
     * ArrayAdapter's (Context, int, T[]) constructor casts the whole inflated row to
     * TextView, so a custom layout root (LinearLayout) crashes with ClassCastException.
     * Six rows are inflated per dialog, so skipping view recycling is fine.
     */
    private static final class FormatAdapter extends BaseAdapter {
        private final Activity activity;
        private final String[] labels;

        FormatAdapter(Activity activity, String[] labels) {
            this.activity = activity;
            this.labels = labels;
        }

        @Override
        public int getCount() {
            return labels.length;
        }

        @Override
        public Object getItem(int position) {
            return labels[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_spinner, parent, false);
            ((TextView) row.findViewById(R.id.txtValue)).setText(labels[position]);
            return row;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_spinner_dropdown, parent, false);
            ((TextView) row.findViewById(R.id.txtValue)).setText(labels[position]);
            return row;
        }
    }
    /** Lists directories; ".." rows resolve to the parent directory. */
    private static final class DirAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<File> dirs = new ArrayList<>();
        private File base;

        DirAdapter(Activity activity) {
            this.activity = activity;
        }

        void setDirs(List<File> list, File baseDir) {
            dirs.clear();
            dirs.addAll(list);
            base = baseDir;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return dirs.size();
        }

        @Override
        public Object getItem(int position) {
            File f = dirs.get(position);
            if ("..".equals(f.getName())) {
                File parent = base.getParentFile();
                return parent == null ? base : parent;
            }
            return f;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = convertView instanceof TextView ? (TextView) convertView : null;
            if (tv == null) {
                tv = new TextView(activity);
                tv.setTextSize(14);
                tv.setTextColor(U.color(activity, R.color.text));
                tv.setPadding(U.dp(activity, 12), U.dp(activity, 12), U.dp(activity, 12), U.dp(activity, 12));
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            }
            File f = dirs.get(position);
            String name = f.getName();
            tv.setText("..".equals(name) ? ".. （上一级）" : name);
            tv.setTextColor(U.color(activity, "..".equals(name) ? R.color.text2 : R.color.text));
            return tv;
        }
    }
}
