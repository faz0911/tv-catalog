package com.example.tvwebview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/** ImageView that is always 16:9 based on its width. */
public class AspectImageView extends ImageView {
    public AspectImageView(Context c) { super(c); }
    public AspectImageView(Context c, AttributeSet a) { super(c, a); }
    public AspectImageView(Context c, AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = View.MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, w * 9 / 16);
    }
}
