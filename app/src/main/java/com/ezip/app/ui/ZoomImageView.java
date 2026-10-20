package com.ezip.app.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** ImageView with pinch zoom, pan and double tap zoom (kept dependency free). */
public class ZoomImageView extends ImageView {

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float baseScale = 1f;
    private float currentScale = 1f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        setClickable(true);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float target = currentScale * factor;
                if (target < baseScale) {
                    factor = baseScale / currentScale;
                } else if (target > baseScale * 8f) {
                    factor = baseScale * 8f / currentScale;
                }
                if (factor == 1f) return true;
                currentScale *= factor;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                constrain();
                setImageMatrix(matrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentScale > baseScale * 1.4f) {
                    fitToView();
                } else {
                    float target = Math.min(baseScale * 2.8f, baseScale * 8f);
                    float factor = target / currentScale;
                    currentScale = target;
                    matrix.postScale(factor, factor, e.getX(), e.getY());
                    constrain();
                    setImageMatrix(matrix);
                }
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    private void fitToView() {
        Drawable drawable = getDrawable();
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (drawable == null || viewWidth == 0 || viewHeight == 0) return;
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) return;

        float scale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        baseScale = scale;
        currentScale = scale;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f);
        setImageMatrix(matrix);
    }

    private void constrain() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float width = drawable.getIntrinsicWidth() * scale;
        float height = drawable.getIntrinsicHeight() * scale;
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        if (width <= viewWidth) {
            transX = (viewWidth - width) / 2f;
        } else {
            transX = Math.max(viewWidth - width, Math.min(0, transX));
        }
        if (height <= viewHeight) {
            transY = (viewHeight - height) / 2f;
        } else {
            transY = Math.max(viewHeight - height, Math.min(0, transY));
        }
        values[Matrix.MTRANS_X] = transX;
        values[Matrix.MTRANS_Y] = transY;
        matrix.setValues(values);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && currentScale > baseScale) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();
                        matrix.postTranslate(dx, dy);
                        constrain();
                        setImageMatrix(matrix);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }
}
