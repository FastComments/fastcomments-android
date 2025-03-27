package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;

/**
 * Dialog for displaying a full-screen, zoomable image
 */
public class FullImageDialog extends Dialog {

    private final String imageUrl;

    public FullImageDialog(@NonNull Context context, String imageUrl) {
        super(context);
        this.imageUrl = imageUrl;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_full_image);
        
        // Make dialog fill the screen
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, 
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            
            // Set window flags for immersive mode
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        
        // Set up close button
        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());
        
        // Set up photo view with the image
        PhotoView photoView = findViewById(R.id.photoView);
        
        // Load image with Glide
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(getContext())
                    .load(imageUrl)
                    .error(R.drawable.image_placeholder)
                    .into(photoView);
        }
    }
}