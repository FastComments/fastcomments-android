package com.fastcomments.sdk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;

import java.util.List;

/**
 * Adapter for displaying images in a post's ViewPager
 */
public class PostImagesAdapter extends RecyclerView.Adapter<PostImagesAdapter.ImageViewHolder> {

    private final Context context;
    private final List<FeedPostMediaItem> mediaItems;
    private final OnImageClickListener listener;

    /**
     * Interface for image click callbacks
     */
    public interface OnImageClickListener {
        void onImageClick(FeedPostMediaItem mediaItem);
    }

    public PostImagesAdapter(Context context, List<FeedPostMediaItem> mediaItems, OnImageClickListener listener) {
        this.context = context;
        this.mediaItems = mediaItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        FeedPostMediaItem mediaItem = mediaItems.get(position);
        holder.bind(mediaItem);
    }

    @Override
    public int getItemCount() {
        return mediaItems != null ? mediaItems.size() : 0;
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final ImageView playButton;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.postImageView);
            playButton = itemView.findViewById(R.id.playButton);

            // Set click listener to show full image gallery
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FeedPostMediaItem mediaItem = mediaItems.get(position);
                    
                    // Show gallery mode starting at the clicked image position
                    new FullImageDialog(context, mediaItems, position).show();
                    
                    // Also notify the listener if set
                    if (listener != null) {
                        listener.onImageClick(mediaItem);
                    }
                }
            });
        }

        void bind(FeedPostMediaItem mediaItem) {
            if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                // Select best size for display
                FeedPostMediaItemAsset bestSizeAsset = selectBestImageSize(mediaItem.getSizes());
                
                if (bestSizeAsset != null && bestSizeAsset.getSrc() != null) {
                    // Pre-set a minimum height for consistency
                    imageView.setMinimumHeight(context.getResources().getDimensionPixelSize(R.dimen.feed_image_height));
                    
                    Glide.with(context)
                            .load(bestSizeAsset.getSrc())
                            .centerCrop()
                            .transition(DrawableTransitionOptions.withCrossFade(300))
                            .error(R.drawable.image_placeholder)
                            .into(imageView);
                }
            }

            // Determine if it's a video
            boolean isVideo = mediaItem.getLinkUrl() != null &&
                             (mediaItem.getLinkUrl().contains("youtube") ||
                              mediaItem.getLinkUrl().contains("vimeo") ||
                              mediaItem.getLinkUrl().contains(".mp4"));
            
            playButton.setVisibility(isVideo ? View.VISIBLE : View.GONE);
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
        
        /**
         * Select the best image size based on device display metrics
         */
        private FeedPostMediaItemAsset selectBestImageSize(List<FeedPostMediaItemAsset> sizes) {
            if (sizes == null || sizes.isEmpty()) {
                return null;
            }
            
            // If there's only one size, use it
            if (sizes.size() == 1) {
                return sizes.get(0);
            }
            
            // Get screen width for comparison
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            
            // Target a size that's close to the screen width for optimal display
            double optimalWidth = screenWidth;
            double maxAcceptableWidth = screenWidth * 1.5;
            
            FeedPostMediaItemAsset bestMatch = null;
            double smallestDiff = Double.MAX_VALUE;
            
            // First pass: find close matches to optimal width
            for (FeedPostMediaItemAsset asset : sizes) {
                if (asset == null || asset.getW() == null || asset.getSrc() == null) {
                    continue;
                }
                
                double width = asset.getW();
                double diff = Math.abs(width - optimalWidth);
                
                // If width is within acceptable range and has smaller difference than current best match
                if (width <= maxAcceptableWidth && diff < smallestDiff) {
                    bestMatch = asset;
                    smallestDiff = diff;
                }
            }
            
            // If no match found in optimal range, just use the largest that's not excessively large
            if (bestMatch == null) {
                double largestAcceptableWidth = 0;
                
                for (FeedPostMediaItemAsset asset : sizes) {
                    if (asset == null || asset.getW() == null || asset.getSrc() == null) {
                        continue;
                    }
                    
                    double width = asset.getW();
                    
                    // Find largest image that's not too oversized
                    if (width > largestAcceptableWidth && width <= maxAcceptableWidth * 2) {
                        bestMatch = asset;
                        largestAcceptableWidth = width;
                    }
                }
                
                // If still no match, use the first valid asset
                if (bestMatch == null) {
                    for (FeedPostMediaItemAsset asset : sizes) {
                        if (asset != null && asset.getSrc() != null) {
                            return asset;
                        }
                    }
                }
            }
            
            // Return best match or first asset if no match found
            return bestMatch != null ? bestMatch : sizes.get(0);
        }
    }
}