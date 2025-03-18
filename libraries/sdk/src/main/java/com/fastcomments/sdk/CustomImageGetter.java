package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * Custom image getter for handling images in HTML content
 */
public class CustomImageGetter implements Html.ImageGetter {
    private final Context context;
    private final TextView textView;

    /**
     * Constructor for CustomImageGetter
     * @param context Context for loading images
     * @param textView TextView to display images
     */
    public CustomImageGetter(Context context, TextView textView) {
        this.context = context;
        this.textView = textView;
    }

    @Override
    public Drawable getDrawable(String source) {
        final URLDrawable urlDrawable = new URLDrawable();
        urlDrawable.setBounds(0, 0, textView.getWidth(), 100);

        // Use Glide to asynchronously load the image from the URL
        Glide.with(context)
                .asBitmap()
                .load(source)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Create a BitmapDrawable from the loaded Bitmap
                        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), resource);
                        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                        ViewGroup.LayoutParams textViewLayout = textView.getLayoutParams();
                        textViewLayout.height = drawable.getIntrinsicHeight();
                        textView.setLayoutParams(textViewLayout);
                        urlDrawable.setDrawable(drawable);

                        // Refresh the TextView so that the new image is displayed
                        textView.setText(textView.getText());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Handle cleanup if needed
                    }
                });

        // Return the URLDrawable (which will be updated asynchronously)
        return urlDrawable;
    }
}