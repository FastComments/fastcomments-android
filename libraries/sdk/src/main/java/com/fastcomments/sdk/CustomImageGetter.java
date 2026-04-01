package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * Custom image getter for handling images (including animated GIFs) in HTML content.
 */
public class CustomImageGetter implements Html.ImageGetter {
    private final Context context;
    private final TextView textView;

    public CustomImageGetter(Context context, TextView textView) {
        this.context = context;
        this.textView = textView;
    }

    @Override
    public Drawable getDrawable(String source) {
        final URLDrawable urlDrawable = new URLDrawable();
        urlDrawable.setBounds(0, 0, textView.getWidth(), 100);

        boolean isGif = source != null && (source.endsWith(".gif") || source.contains(".gif?") || source.contains("/giphy.gif"));

        if (isGif) {
            loadGif(source, urlDrawable);
        } else {
            loadBitmap(source, urlDrawable);
        }

        return urlDrawable;
    }

    private void loadGif(String source, URLDrawable urlDrawable) {
        Glide.with(context)
                .asGif()
                .load(source)
                .into(new CustomTarget<GifDrawable>() {
                    @Override
                    public void onResourceReady(@NonNull GifDrawable resource, @Nullable Transition<? super GifDrawable> transition) {
                        resource.setBounds(0, 0, resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                        resource.setLoopCount(GifDrawable.LOOP_FOREVER);

                        resource.setCallback(new Drawable.Callback() {
                            @Override
                            public void invalidateDrawable(@NonNull Drawable who) {
                                textView.invalidate();
                            }

                            @Override
                            public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                                textView.postDelayed(what, when - android.os.SystemClock.uptimeMillis());
                            }

                            @Override
                            public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                                textView.removeCallbacks(what);
                            }
                        });

                        urlDrawable.setDrawable(resource);
                        urlDrawable.setBounds(0, 0, resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                        resource.start();
                        textView.setText(textView.getText());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    private void loadBitmap(String source, URLDrawable urlDrawable) {
        Glide.with(context)
                .asBitmap()
                .load(source)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), resource);
                        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                        urlDrawable.setDrawable(drawable);
                        urlDrawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                        textView.setText(textView.getText());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }
}
