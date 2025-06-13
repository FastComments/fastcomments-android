package com.fastcomments.sdk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * Adapter for displaying images in the full-screen gallery ViewPager
 */
public class GalleryImageAdapter extends RecyclerView.Adapter<GalleryImageAdapter.GalleryImageViewHolder> {

    private final Context context;
    private final List<FeedPostMediaItem> mediaItems;

    public GalleryImageAdapter(Context context, List<FeedPostMediaItem> mediaItems) {
        this.context = context;
        this.mediaItems = mediaItems;
    }

    @NonNull
    @Override
    public GalleryImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_gallery_image, parent, false);
        return new GalleryImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryImageViewHolder holder, int position) {
        FeedPostMediaItem mediaItem = mediaItems.get(position);
        holder.bind(mediaItem);
    }

    @Override
    public int getItemCount() {
        return mediaItems != null ? mediaItems.size() : 0;
    }

    /**
     * Get the best quality image URL for full-screen viewing
     * For full-screen viewing, we want the highest quality image available
     *
     * @param mediaItem The media item to get the URL from
     * @return The URL of the highest quality image, or null if not available
     */
    private String getBestQualityImageUrl(FeedPostMediaItem mediaItem) {
        if (mediaItem == null || mediaItem.getSizes() == null || mediaItem.getSizes().isEmpty()) {
            return null;
        }

        // Find the image with the highest resolution (largest width)
        FeedPostMediaItemAsset highestQualityAsset = null;
        double maxWidth = 0;

        for (FeedPostMediaItemAsset asset : mediaItem.getSizes()) {
            if (asset != null && asset.getSrc() != null && asset.getW() != null) {
                double width = asset.getW();
                if (width > maxWidth) {
                    maxWidth = width;
                    highestQualityAsset = asset;
                }
            }
        }

        return highestQualityAsset != null ? highestQualityAsset.getSrc() : null;
    }

    class GalleryImageViewHolder extends RecyclerView.ViewHolder {
        private final PhotoView photoView;

        GalleryImageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photoView);
        }

        void bind(FeedPostMediaItem mediaItem) {
            String imageUrl = getBestQualityImageUrl(mediaItem);
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .error(R.drawable.image_placeholder)
                        .into(photoView);
                // Note: we intentionally don't use centerCrop() here as this is a full-screen
                // image view with zoom capabilities via PhotoView
            } else {
                // Set placeholder if no valid image URL
                photoView.setImageResource(R.drawable.image_placeholder);
            }
        }
    }
}