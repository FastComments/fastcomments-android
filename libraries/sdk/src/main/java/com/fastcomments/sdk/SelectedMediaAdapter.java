package com.fastcomments.sdk;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying selected media items in a RecyclerView
 */
public class SelectedMediaAdapter extends RecyclerView.Adapter<SelectedMediaAdapter.MediaViewHolder> {

    private final Context context;
    private final List<Uri> mediaUris = new ArrayList<>();
    private final OnMediaItemListener listener;

    /**
     * Interface for handling media item interactions
     */
    public interface OnMediaItemListener {
        void onRemoveMedia(int position);
    }

    public SelectedMediaAdapter(Context context, OnMediaItemListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.selected_media_item, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        Uri mediaUri = mediaUris.get(position);
        holder.bind(mediaUri, position);
    }

    @Override
    public int getItemCount() {
        return mediaUris.size();
    }

    /**
     * Add a new media item to the adapter
     * 
     * @param uri Uri of the media item
     */
    public void addMedia(Uri uri) {
        mediaUris.add(uri);
        notifyItemInserted(mediaUris.size() - 1);
    }

    /**
     * Remove a media item from the adapter
     * 
     * @param position Position of the media item to remove
     */
    public void removeMedia(int position) {
        if (position >= 0 && position < mediaUris.size()) {
            mediaUris.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mediaUris.size() - position);
        }
    }

    /**
     * Get all media URIs
     * 
     * @return List of media URIs
     */
    public List<Uri> getMediaUris() {
        return new ArrayList<>(mediaUris);
    }

    /**
     * Clear all media items from the adapter
     */
    public void clearMedia() {
        mediaUris.clear();
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for media items
     */
    class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mediaImageView;
        private final ImageButton removeButton;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaImageView = itemView.findViewById(R.id.selectedMediaImageView);
            removeButton = itemView.findViewById(R.id.removeMediaButton);
        }

        void bind(Uri mediaUri, int position) {
            // Load the image with Glide
            Glide.with(context)
                    .load(mediaUri)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(mediaImageView);

            // Set up the remove button
            removeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveMedia(position);
                }
            });
        }
    }
}