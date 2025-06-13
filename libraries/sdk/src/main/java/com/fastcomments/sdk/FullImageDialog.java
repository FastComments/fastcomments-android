package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.fastcomments.model.FeedPostMediaItem;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * Dialog for displaying a full-screen, zoomable image or gallery of images
 */
public class FullImageDialog extends Dialog {

    private final String imageUrl;
    private final List<FeedPostMediaItem> mediaItems;
    private final int startPosition;
    private final boolean isGalleryMode;

    /**
     * Constructor for single image mode
     */
    public FullImageDialog(@NonNull Context context, String imageUrl) {
        super(context);
        this.imageUrl = imageUrl;
        this.mediaItems = null;
        this.startPosition = 0;
        this.isGalleryMode = false;
    }

    /**
     * Constructor for gallery mode with multiple images
     */
    public FullImageDialog(@NonNull Context context, List<FeedPostMediaItem> mediaItems, int startPosition) {
        super(context);
        this.imageUrl = null;
        this.mediaItems = mediaItems;
        this.startPosition = startPosition;
        this.isGalleryMode = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        if (isGalleryMode) {
            setContentView(R.layout.dialog_full_image_gallery);
            setupGalleryMode();
        } else {
            setContentView(R.layout.dialog_full_image);
            setupSingleImageMode();
        }
        
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
    }

    private void setupSingleImageMode() {
        // Set up photo view with the image
        PhotoView photoView = findViewById(R.id.photoView);
        
        // Load image with Glide
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(getContext())
                    .load(imageUrl)
                    .error(R.drawable.image_placeholder)
                    .into(photoView);
            // Note: we intentionally don't use centerCrop() here as this is a full-screen
            // image view with zoom capabilities via PhotoView
        }
    }

    private void setupGalleryMode() {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return;
        }

        ViewPager2 imageViewPager = findViewById(R.id.imageViewPager);
        TextView imageCounter = findViewById(R.id.imageCounter);

        // Set up the adapter
        GalleryImageAdapter adapter = new GalleryImageAdapter(getContext(), mediaItems);
        imageViewPager.setAdapter(adapter);

        // Set the starting position
        imageViewPager.setCurrentItem(startPosition, false);

        // Show image counter if there are multiple images
        if (mediaItems.size() > 1) {
            imageCounter.setVisibility(android.view.View.VISIBLE);
            updateImageCounter(startPosition, imageCounter);

            // Set up page change callback to update counter
            imageViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    updateImageCounter(position, imageCounter);
                }
            });
        } else {
            imageCounter.setVisibility(android.view.View.GONE);
        }
    }

    private void updateImageCounter(int position, TextView imageCounter) {
        if (mediaItems != null && !mediaItems.isEmpty()) {
            String counterText = String.format("%d of %d", position + 1, mediaItems.size());
            imageCounter.setText(counterText);
        }
    }
}