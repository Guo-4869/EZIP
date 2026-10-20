package com.ezip.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.ezip.app.archive.CancelToken;
import com.ezip.app.archive.ProgressSink;
import com.ezip.app.ui.Kind;
import com.ezip.app.ui.TaskRunner;
import com.ezip.app.ui.U;
import com.ezip.app.ui.ZoomImageView;
import com.ezip.app.util.FileX;
import com.ezip.app.util.Ooxml;

import java.io.File;

/** Preview for images, text, PDF, video, audio and Office documents. */
public class PreviewActivity extends Activity {

    private static final String EXTRA_PATH = "path";
    private static final int MAX_TEXT_BYTES = 512 * 1024;

    public static void open(Activity activity, File file) {
        Intent intent = new Intent(activity, PreviewActivity.class);
        intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
        activity.startActivity(intent);
    }

    private File file;
    private FrameLayout content;
    private LinearLayout bottomBar;
    private LinearLayout audioBar;
    private TextView txtPage;
    private TextView txtTime;
    private SeekBar seekBar;
    private ImageView btnPlay;

    private VideoView videoView;
    private MediaPlayer player;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor pdfDescriptor;
    private int pageIndex;
    private int pageCount;
    private boolean seekDragging;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player == null || txtTime == null) return;
            try {
                int position = player.getCurrentPosition();
                int duration = player.getDuration();
                if (!seekDragging && duration > 0) {
                    seekBar.setProgress((int) (position * 1000L / duration));
                }
                txtTime.setText(time(position) + " / " + time(duration));
            } catch (IllegalStateException ignored) {
                return;
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        file = path == null ? null : new File(path);
        if (file == null || !file.isFile()) {
            U.toast(this, "文件不存在");
            finish();
            return;
        }

        content = (FrameLayout) findViewById(R.id.content);
        bottomBar = (LinearLayout) findViewById(R.id.bottomBar);
        audioBar = (LinearLayout) findViewById(R.id.audioBar);
        txtPage = (TextView) findViewById(R.id.txtPage);
        txtTime = (TextView) findViewById(R.id.txtTime);
        seekBar = (SeekBar) findViewById(R.id.seek);
        btnPlay = (ImageView) findViewById(R.id.btnPlay);

        TextView txtTitle = (TextView) findViewById(R.id.txtTitle);
        txtTitle.setText(file.getName());

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnOpenWith).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                U.openWith(PreviewActivity.this, file);
            }
        });
        findViewById(R.id.btnPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pageIndex > 0) renderPdfPage(pageIndex - 1);
            }
        });
        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pageIndex < pageCount - 1) renderPdfPage(pageIndex + 1);
            }
        });

        dispatch();
    }

    private void dispatch() {
        Kind kind = Kind.of(file.getName());
        switch (kind) {
            case IMAGE:
                showImage();
                break;
            case VIDEO:
                showVideo();
                break;
            case AUDIO:
                showAudio();
                break;
            case PDF:
                showPdf();
                break;
            case TEXT:
            case CODE:
                showText();
                break;
            case DOC:
            case PPT:
            case XLS:
                showDocument();
                break;
            case ARCHIVE:
                ArchiveActivity.open(this, file);
                finish();
                break;
            default:
                showUnsupported();
                break;
        }
    }

    // ------------------------------------------------------------------ image

    private void showImage() {
        Bitmap bitmap = decodeSampledBitmap(file);
        if (bitmap == null) {
            showUnsupported();
            return;
        }
        ZoomImageView image = new ZoomImageView(this);
        image.setImageBitmap(bitmap);
        content.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private Bitmap decodeSampledBitmap(File source) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int target = 2048;
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > target) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ video

    private void showVideo() {
        videoView = new VideoView(this);
        content.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showUnsupported();
                return true;
            }
        });
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                videoView.start();
            }
        });
        try {
            videoView.setVideoPath(file.getAbsolutePath());
        } catch (Exception e) {
            showUnsupported();
        }
    }

    // ------------------------------------------------------------------ audio

    private void showAudio() {
        audioBar.setVisibility(View.VISIBLE);
        LinearLayout cover = new LinearLayout(this);
        cover.setOrientation(LinearLayout.VERTICAL);
        cover.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_play);
        icon.setColorFilter(U.color(this, R.color.primary));
        cover.addView(icon, new LinearLayout.LayoutParams(U.dp(this, 56), U.dp(this, 56)));

        TextView name = U.text(this, file.getName(), 15, U.color(this, R.color.text), 24, 12);
        name.setGravity(Gravity.CENTER);
        cover.addView(name);

        TextView size = U.text(this, FileX.formatSize(file.length()), 12, U.color(this, R.color.text2), 24, 0);
        size.setGravity(Gravity.CENTER);
        cover.addView(size);
        content.addView(cover, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
        } catch (Exception e) {
            showUnsupported();
            return;
        }

        seekBar.setMax(1000);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || player == null || player.getDuration() <= 0) return;
                player.seekTo((int) (player.getDuration() * (progress / 1000.0)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seekDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seekDragging = false;
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                btnPlay.setImageResource(R.drawable.ic_play);
            }
        });
        handler.post(ticker);
    }

    private void togglePlay() {
        if (player == null) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                btnPlay.setImageResource(R.drawable.ic_play);
            } else {
                player.start();
                btnPlay.setImageResource(R.drawable.ic_pause);
            }
        } catch (IllegalStateException e) {
            U.toast(this, "无法播放该音频");
        }
    }

    private String time(int millis) {
        int totalSeconds = Math.max(0, millis) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ------------------------------------------------------------------ pdf

    private void showPdf() {
        try {
            pdfDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);
            pageCount = pdfRenderer.getPageCount();
        } catch (Exception e) {
            showUnsupported();
            return;
        }
        if (pageCount <= 0) {
            showUnsupported();
            return;
        }
        bottomBar.setVisibility(View.VISIBLE);
        renderPdfPage(0);
    }

    private void renderPdfPage(int index) {
        if (pdfRenderer == null) return;
        PdfRenderer.Page page = null;
        try {
            page = pdfRenderer.openPage(index);
            int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1600);
            float scale = targetWidth / (float) page.getWidth();
            int height = Math.max(1, Math.round(page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            pageIndex = index;
            txtPage.setText(getString(R.string.page_of, index + 1, pageCount));

            ZoomImageView image = new ZoomImageView(this);
            image.setImageBitmap(bitmap);
            content.removeAllViews();
            content.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            U.toast(this, "无法渲染该页 PDF");
        } finally {
            if (page != null) page.close();
        }
    }

    // ------------------------------------------------------------------ text & documents

    private void showText() {
        try {
            String text = FileX.readText(file, MAX_TEXT_BYTES);
            if (text.length() < file.length()) {
                text = text + "\n\n…（文件较大，仅显示前 512 KB）";
            }
            showTextContent(text, true);
        } catch (Exception e) {
            showUnsupported();
        }
    }

    private void showDocument() {
        TaskRunner.start(this, getString(R.string.loading), file.getName(),
                new TaskRunner.Work() {
                    @Override
                    public void run(ProgressSink sink, CancelToken token) throws Exception {
                        finalText[0] = Ooxml.extractText(file, 200000);
                    }
                },
                new TaskRunner.Done() {
                    @Override
                    public void onFinished(Exception error, boolean cancelled) {
                        if (error != null || finalText[0] == null || finalText[0].isEmpty()) {
                            showUnsupported();
                            return;
                        }
                        showTextContent(finalText[0], false);
                    }
                });
    }

    private final String[] finalText = new String[1];

    private void showTextContent(String text, boolean monospace) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(monospace ? 13 : 15);
        if (monospace) view.setTypeface(Typeface.MONOSPACE);
        view.setTextColor(U.color(this, R.color.text));
        view.setTextIsSelectable(true);
        view.setLineSpacing(U.dp(this, 3), 1f);
        int pad = U.dp(this, 16);
        view.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(U.color(this, R.color.surface));
        scroll.addView(view);
        content.removeAllViews();
        content.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------------ fallback

    private void showUnsupported() {
        content.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(U.dp(this, 32), U.dp(this, 32), U.dp(this, 32), U.dp(this, 32));

        TextView title = U.text(this, getString(R.string.preview_unsupported), 16, U.color(this, R.color.text), 0, 0);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView desc = U.text(this, getString(R.string.preview_unsupported_desc), 13,
                U.color(this, R.color.text2), 0, 0);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = U.dp(this, 8);
        root.addView(desc, descLp);

        TextView button = new TextView(this);
        button.setText(getString(R.string.open_with));
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.btn_primary);
        button.setPadding(U.dp(this, 20), U.dp(this, 11), U.dp(this, 20), U.dp(this, 11));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                U.openWith(PreviewActivity.this, file);
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonLp.topMargin = U.dp(this, 20);
        root.addView(button, buttonLp);

        content.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {
                // view already detached
            }
        }
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // nothing to release
            }
            player = null;
        }
        if (pdfRenderer != null) {
            try {
                pdfRenderer.close();
            } catch (Exception ignored) {
                // already closed
            }
            pdfRenderer = null;
        }
        if (pdfDescriptor != null) {
            try {
                pdfDescriptor.close();
            } catch (Exception ignored) {
                // already closed
            }
            pdfDescriptor = null;
        }
        super.onDestroy();
    }
}
