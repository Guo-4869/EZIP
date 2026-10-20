package com.ezip.app.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.ezip.app.R;
import com.ezip.app.util.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * File list adapter.
 *
 * <p>Rows are pre-built {@link FileItem}s: name, size, mtime and the display string were
 * computed once on a background thread, so {@link #getView} performs <b>no filesystem
 * access at all</b>. That is what keeps scrolling smooth in folders with thousands of files.</p>
 */
public class FileAdapter extends BaseAdapter {

    public interface Listener {
        void onFileClick(File file);

        void onFileLongClick(File file);
    }

    private final Activity activity;
    private final Listener listener;
    private final List<FileItem> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private boolean selectionMode;
    private String highlightPath;

    public FileAdapter(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void setItems(List<FileItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public List<FileItem> getItems() {
        return items;
    }

    /** Convenience for callers that only care about the files. */
    public List<File> getFiles() {
        List<File> files = new ArrayList<>(items.size());
        for (FileItem item : items) files.add(item.file);
        return files;
    }

    /** Briefly highlights a freshly created file (set to null to clear). */
    public void setHighlight(String path) {
        highlightPath = path;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        if (!on) selected.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelected(Set<String> paths) {
        selected.clear();
        if (paths != null) selected.addAll(paths);
        notifyDataSetChanged();
    }

    public Set<String> getSelected() {
        return selected;
    }

    public void toggle(File file) {
        String key = file.getAbsolutePath();
        if (!selected.remove(key)) selected.add(key);
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (FileItem item : items) selected.add(item.file.getAbsolutePath());
        notifyDataSetChanged();
    }

    public boolean isSelected(File file) {
        return selected.contains(file.getAbsolutePath());
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        Holder holder;
        if (row == null) {
            row = LayoutInflater.from(activity).inflate(R.layout.item_file, parent, false);
            holder = new Holder(row);
            row.setTag(holder);
        } else {
            holder = (Holder) row.getTag();
        }

        final FileItem item = items.get(position);
        final File file = item.file;
        Kind kind = item.directory ? Kind.FOLDER : Kind.of(item.name);

        holder.name.setText(item.name);
        holder.meta.setText(item.meta);

        if (kind == Kind.FOLDER) {
            holder.badge.setVisibility(View.GONE);
            holder.icon.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setVisibility(View.GONE);
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(kind.label(item.name));
            holder.badge.setBackground(U.roundRect(activity, U.color(activity, kind.colorRes), 10));
        }

        boolean isSelected = selectionMode && selected.contains(file.getAbsolutePath());
        boolean highlighted = highlightPath != null && highlightPath.equals(file.getAbsolutePath());
        holder.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.chevron.setVisibility(!selectionMode && item.directory ? View.VISIBLE : View.GONE);
        if (isSelected || highlighted) {
            row.setBackgroundColor(U.color(activity, R.color.primary_pale));
        } else {
            row.setBackgroundResource(R.drawable.item_bg);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFileClick(file);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onFileLongClick(file);
                return true;
            }
        });
        return row;
    }

    private static final class Holder {
        final TextView badge;
        final ImageView icon;
        final TextView name;
        final TextView meta;
        final ImageView check;
        final ImageView chevron;

        Holder(View row) {
            badge = (TextView) row.findViewById(R.id.badge);
            icon = (ImageView) row.findViewById(R.id.icon);
            name = (TextView) row.findViewById(R.id.name);
            meta = (TextView) row.findViewById(R.id.meta);
            check = (ImageView) row.findViewById(R.id.check);
            chevron = (ImageView) row.findViewById(R.id.chevron);
        }
    }
}
