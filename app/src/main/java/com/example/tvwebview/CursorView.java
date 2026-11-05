package com.example.tvwebview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Transparent overlay that draws a mouse-style pointer at (x, y). */
public class CursorView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private float x, y;

    public CursorView(Context c) { super(c); init(); }
    public CursorView(Context c, AttributeSet a) { super(c, a); init(); }
    public CursorView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFF000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * d);
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 22 * d);
        arrow.lineTo(5.5f * d, 17 * d);
        arrow.lineTo(9.5f * d, 26 * d);
        arrow.lineTo(13 * d, 24.5f * d);
        arrow.lineTo(9 * d, 16 * d);
        arrow.lineTo(16 * d, 16 * d);
        arrow.close();
        setFocusable(false);
        setClickable(false);
    }

    public void setPos(float x, float y) {
        this.x = x;
        this.y = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, stroke);
        canvas.restore();
    }
}
