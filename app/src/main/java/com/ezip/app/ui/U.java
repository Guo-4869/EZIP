package com.ezip.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ezip.app.R;
import com.ezip.app.provider.EzFileProvider;
import com.ezip.app.util.FileX;

import java.io.File;

/** Tiny UI helpers: dp, toast, dialogs, rounded drawables, file summaries. */
public final class U {

    private U() {
    }

    public static int dp(Context ctx, float value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context ctx, String message) {
        if (ctx == null || message == null) return;
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }

    public static GradientDrawable roundRect(Context ctx, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(ctx, radiusDp));
        return drawable;
    }

    public static GradientDrawable topRounded(Context ctx, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        float r = dp(ctx, radiusDp);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    public static Dialog newDialog(Activity activity, int layoutRes) {
        View content = LayoutInflater.from(activity).inflate(layoutRes, null);
        return newDialog(activity, content, true);
    }

    public static Dialog newDialog(Activity activity, View content, boolean wrapWidth) {
        Dialog dialog = new Dialog(activity, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int horizontal = Math.min(U.dp(activity, 24), activity.getResources().getDisplayMetrics().widthPixels / 12);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = activity.getResources().getDisplayMetrics().widthPixels - horizontal * 2;
            window.setAttributes(lp);
            capHeight(activity, content, lp.width);
        }
        content.setBackground(roundRect(activity, color(activity, R.color.surface), 18));
        return dialog;
    }

    /** Keeps tall scrolling dialogs inside the screen instead of clipping their buttons. */
    private static void capHeight(Activity activity, View content, int width) {
        if (!(content instanceof ScrollView)) return;
        int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f);
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (content.getMeasuredHeight() <= maxHeight) return;
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null) return;
        params.height = maxHeight;
        content.setLayoutParams(params);
    }

    public static Dialog newSheet(Activity activity) {
        Dialog dialog = new Dialog(activity, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    public static TextView text(Context ctx, String value, int sizeSp, int color, float padDpH, float padDpV) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setPadding(dp(ctx, padDpH), dp(ctx, padDpV), dp(ctx, padDpH), dp(ctx, padDpV));
        return tv;
    }

    public static int color(Context ctx, int colorRes) {
        return ctx.getResources().getColor(colorRes);
    }

    /** Strips characters that are not valid in a file name. */
    public static String safeName(String name) {
        if (name == null) return "";
        String cleaned = name.replace('/', '_').replace('\\', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_').trim();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        return cleaned;
    }

    public static boolean hasText(String s) {
        return s != null && s.trim().length() > 0;
    }

    /** "内部存储/Download" style label for a folder, used in result messages. */
    public static String folderLabel(File dir) {
        return FileX.folderLabel(dir, android.os.Environment.getExternalStorageDirectory());
    }

    /** Hands a file to another app through our content provider. */
    public static void openWith(Activity activity, File file) {
        if (file == null || !file.isFile()) {
            toast(activity, "文件不存在");
            return;
        }
        try {
            Uri uri = EzFileProvider.uriFor(file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, EzFileProvider.mimeOf(file.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.open_with)));
        } catch (ActivityNotFoundException e) {
            toast(activity, "没有可以打开该文件的应用");
        } catch (Exception e) {
            toast(activity, String.valueOf(e.getMessage()));
        }
    }
}
