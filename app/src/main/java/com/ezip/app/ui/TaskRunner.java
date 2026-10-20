package com.ezip.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ezip.app.R;
import com.ezip.app.archive.CancelToken;
import com.ezip.app.archive.ProgressSink;
import com.ezip.app.util.FileX;

/**
 * Runs archive work on a background thread behind a cancellable progress dialog.
 * The engine reports bytes; this class throttles the UI updates to ~12/s.
 */
public final class TaskRunner {

    public interface Work {
        void run(ProgressSink sink, CancelToken token) throws Exception;
    }

    public interface Done {
        /** Called on the UI thread, always. error is null on success. */
        void onFinished(Exception error, boolean cancelled);
    }

    private TaskRunner() {
    }

    public static void start(final Activity activity, String title, String initialMessage,
                             final Work work, final Done done) {
        final Dialog dialog = U.newDialog(activity, R.layout.dialog_progress);
        final TextView txtTitle = (TextView) dialog.findViewById(R.id.txtTitle);
        final TextView txtMsg = (TextView) dialog.findViewById(R.id.txtMsg);
        final TextView txtDetail = (TextView) dialog.findViewById(R.id.txtDetail);
        final ProgressBar progress = (ProgressBar) dialog.findViewById(R.id.progress);
        final TextView btnCancel = (TextView) dialog.findViewById(R.id.btnCancel);

        txtTitle.setText(title);
        txtMsg.setText(initialMessage == null ? "" : initialMessage);
        txtDetail.setText("");
        progress.setMax(1000);
        progress.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        final CancelToken token = new CancelToken();
        final Handler main = new Handler(Looper.getMainLooper());

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                token.cancel();
                txtMsg.setText("正在取消…");
                btnCancel.setEnabled(false);
                btnCancel.setTextColor(U.color(activity, R.color.text3));
            }
        });

        try {
            dialog.show();
        } catch (WindowManager.BadTokenException ignored) {
            // activity already gone - the work still runs and the callback is skipped
        }

        final ProgressSink sink = new ProgressSink() {
            private long lastPost;
            private String lastLabel = "";
            private long lastDone = -1;
            private long lastTotal = -1;

            @Override
            public void progress(long done, long total, String label) {
                long now = System.currentTimeMillis();
                boolean changed = !String.valueOf(label).equals(lastLabel);
                if (now - lastPost < 80 && !changed) return;
                if (done == lastDone && total == lastTotal) return;
                lastPost = now;
                lastLabel = String.valueOf(label);
                lastDone = done;
                lastTotal = total;
                final long fDone = done;
                final long fTotal = total;
                final String fLabel = label;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fLabel != null && !fLabel.isEmpty()) txtMsg.setText(fLabel);
                        if (fTotal > 0) {
                            int permille = (int) Math.min(1000, fDone * 1000 / fTotal);
                            progress.setIndeterminate(false);
                            progress.setProgress(permille);
                            txtDetail.setText(FileX.formatSize(fDone) + " / " + FileX.formatSize(fTotal)
                                    + "  ·  " + (permille / 10) + "%");
                        } else {
                            progress.setIndeterminate(true);
                            txtDetail.setText(FileX.formatSize(fDone));
                        }
                    }
                });
            }
        };

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Exception error = null;
                try {
                    work.run(sink, token);
                } catch (Throwable t) {
                    // also covers OutOfMemoryError: show a message instead of killing the app
                    error = (t instanceof Exception) ? (Exception) t : new RuntimeException(t);
                }
                final Exception result = error;
                final boolean cancelled = token.isCancelled();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (dialog.isShowing()) dialog.dismiss();
                        } catch (IllegalArgumentException ignored) {
                            // window already detached
                        }
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        done.onFinished(result, cancelled);
                    }
                });
            }
        }, "ezip-worker");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }
}
