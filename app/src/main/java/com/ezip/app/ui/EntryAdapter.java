package com.ezip.app.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.ezip.app.R;
import com.ezip.app.archive.ArchiveEntryInfo;
import com.ezip.app.util.FileX;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Shows the entries of an archive. */
public class EntryAdapter extends BaseAdapter {

    public interface Listener {
        void onEntryClick(ArchiveEntryInfo entry);
    }

    private final Activity activity;
    private final Listener listener;
    private final List<ArchiveEntryInfo> entries = new ArrayList<>();

    public EntryAdapter(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void setEntries(List<ArchiveEntryInfo> list) {
        entries.clear();
        if (list != null) entries.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public Object getItem(int position) {
        return entries.get(position);
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
            row = LayoutInflater.from(activity).inflate(R.layout.item_entry, parent, false);
            holder = new Holder(row);
            row.setTag(holder);
        } else {
            holder = (Holder) row.getTag();
        }
        final ArchiveEntryInfo entry = entries.get(position);
        String name = entry.name;
        while (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        int slash = name.lastIndexOf('/');
        String label = slash >= 0 ? name.substring(slash + 1) : name;

        holder.name.setText(label);
        if (entry.directory) {
            holder.meta.setText("文件夹");
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.icon.setColorFilter(U.color(activity, R.color.primary));
        } else {
            String size = FileX.formatSize(entry.size);
            String time = FileX.formatTime(entry.time);
            holder.meta.setText(time.isEmpty() ? size : size + "  ·  " + time);
            holder.icon.setImageResource(R.drawable.ic_file);
            holder.icon.setColorFilter(U.color(activity, R.color.text3));
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onEntryClick(entry);
            }
        });
        return row;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView name;
        final TextView meta;

        Holder(View row) {
            icon = (ImageView) row.findViewById(R.id.icon);
            name = (TextView) row.findViewById(R.id.name);
            meta = (TextView) row.findViewById(R.id.meta);
        }
    }
}
