package com.fastcomments.sdk;

import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class URLDrawable extends BitmapDrawable {
    // This drawable will be updated once the image is loaded.
    protected Drawable drawable;

    @Override
    public void draw(Canvas canvas) {
        // Draw the loaded drawable, if available.
        if (drawable != null) {
            drawable.draw(canvas);
        }
    }

    // Setter to update the drawable once Glide loads the image.
    public void setDrawable(Drawable drawable) {
        this.drawable = drawable;
    }
}