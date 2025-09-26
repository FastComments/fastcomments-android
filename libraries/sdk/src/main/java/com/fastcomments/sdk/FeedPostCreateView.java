package com.fastcomments.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.CreateFeedPostParams;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostLink;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;
import com.fastcomments.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * A custom view for creating feed posts with text, images and links
 */
public class FeedPostCreateView extends FrameLayout {

    private static final int MAX_IMAGES = 10;
    private static final int REQUEST_PICK_IMAGE = 100;

    // UI elements
    private ImageView formAvatarImageView;
    private TextView formUserNameTextView;
    private EditText postContentEditText;
    private LinearLayout mediaPreviewContainer;
    private RecyclerView selectedMediaRecyclerView;
    private LinearLayout linkPreviewContainer;
    private TextView linkTitleTextView;
    private TextView linkUrlTextView;
    private ImageButton removeLinkButton;
    private TextView postErrorTextView;
    private ImageButton attachImageButton;
    // private ImageButton attachLinkButton; // Removed link button
    private Button cancelPostButton;
    private Button submitPostButton;
    private ProgressBar postProgressBar;
    private HorizontalScrollView customToolbarScrollView;
    private LinearLayout customToolbarContainer;

    // State variables
    private final List<Uri> selectedMediaUris = new ArrayList<>(0);
    private final List<FeedPostMediaItem> remoteMediaItems = new ArrayList<>(0);
    private SelectedMediaAdapter mediaAdapter;
    private FeedPostLink attachedLink;
    private final Handler mainHandler;
    private FastCommentsFeedSDK sdk;
    private OnPostCreateListener listener;
    private final List<FeedCustomToolbarButton> customButtons = new ArrayList<>(0);

    /**
     * Interface for post create events
     */
    public interface OnPostCreateListener {
        void onPostCreated(FeedPost post);

        void onPostCreateError(String errorMessage);

        void onPostCreateCancelled();

        void onImagePickerRequested();
    }

    // Constructors
    public FeedPostCreateView(@NonNull Context context) {
        super(context);
        mainHandler = new Handler(Looper.getMainLooper());
        init(context);
    }

    public FeedPostCreateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mainHandler = new Handler(Looper.getMainLooper());
        init(context);
    }

    public FeedPostCreateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mainHandler = new Handler(Looper.getMainLooper());
        init(context);
    }

    /**
     * Initialize the view
     */
    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.feed_post_create_form, this, true);

        // Find views
        formAvatarImageView = findViewById(R.id.formAvatarImageView);
        formUserNameTextView = findViewById(R.id.formUserNameTextView);
        postContentEditText = findViewById(R.id.postContentEditText);
        mediaPreviewContainer = findViewById(R.id.mediaPreviewContainer);
        selectedMediaRecyclerView = findViewById(R.id.selectedMediaRecyclerView);
        linkPreviewContainer = findViewById(R.id.linkPreviewContainer);
        linkTitleTextView = findViewById(R.id.linkTitleTextView);
        linkUrlTextView = findViewById(R.id.linkUrlTextView);
        removeLinkButton = findViewById(R.id.removeLinkButton);
        postErrorTextView = findViewById(R.id.postErrorTextView);
        attachImageButton = findViewById(R.id.attachImageButton);
        // attachLinkButton = findViewById(R.id.attachLinkButton); // Removed link button
        cancelPostButton = findViewById(R.id.cancelPostButton);
        submitPostButton = findViewById(R.id.submitPostButton);
        postProgressBar = findViewById(R.id.postProgressBar);
        customToolbarScrollView = findViewById(R.id.customToolbarScrollView);
        customToolbarContainer = findViewById(R.id.customToolbarContainer);

        // Make sure button texts are set explicitly
        submitPostButton.setText(R.string.post);
        cancelPostButton.setText(R.string.cancel);

        // Set up content text watcher to enable/disable submit button
        postContentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Enable submit button only if there's content
                updateSubmitButtonState();
            }
        });

        // Set up selected media adapter
        mediaAdapter = new SelectedMediaAdapter(context, this::removeMedia);
        selectedMediaRecyclerView.setLayoutManager(
                new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        selectedMediaRecyclerView.setAdapter(mediaAdapter);

        // Set up button click listeners
        attachImageButton.setOnClickListener(v -> requestImagePicker());
        // attachLinkButton.setOnClickListener(v -> showAddLinkDialog()); // Removed link button
        removeLinkButton.setOnClickListener(v -> removeLink());
        cancelPostButton.setOnClickListener(v -> cancelPost());
        submitPostButton.setOnClickListener(v -> validateAndSubmitPost());

        // Initially disable submit button and hide media/link previews
        submitPostButton.setEnabled(false);
        submitPostButton.setAlpha(0.5f);
        submitPostButton.setBackgroundColor(0xFF757575); // Gray
        mediaPreviewContainer.setVisibility(GONE);
        linkPreviewContainer.setVisibility(GONE);
        postErrorTextView.setVisibility(GONE);
        postProgressBar.setVisibility(GONE);

        // Set current user info if available
        if (sdk != null && sdk.getCurrentUser() != null) {
            UserSessionInfo userInfo = sdk.getCurrentUser();
            setCurrentUser(userInfo);
        } else {
            // Default state for anonymous user
            formUserNameTextView.setText(R.string.anonymous);
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, formAvatarImageView);
        }
    }

    public void show() {
        setVisibility(View.VISIBLE);
        setClickable(true);
        setEnabled(true);

        // Set current user info if available
        if (sdk != null && sdk.getCurrentUser() != null) {
            UserSessionInfo userInfo = sdk.getCurrentUser();
            setCurrentUser(userInfo);
        } else {
            // Default state for anonymous user
            formUserNameTextView.setText(R.string.anonymous);
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, formAvatarImageView);
        }
    }

    /**
     * Set the SDK instance
     *
     * @param sdk FastCommentsFeedSDK instance
     */
    public void setSDK(FastCommentsFeedSDK sdk) {
        this.sdk = sdk;

        // Add all global toolbar buttons from the SDK
        if (sdk != null) {
            for (FeedCustomToolbarButton button : sdk.getGlobalFeedToolbarButtons()) {
                addCustomToolbarButton(button);
            }
        }
    }

    /**
     * Set the listener for post creation events
     *
     * @param listener OnPostCreateListener implementation
     */
    public void setOnPostCreateListener(OnPostCreateListener listener) {
        this.listener = listener;
    }

    /**
     * Set the current user information
     *
     * @param userInfo UserSessionInfo object
     */
    private void setCurrentUser(UserSessionInfo userInfo) {
        if (userInfo != null) {
            // Set user name
            if (userInfo.getDisplayName() != null && !userInfo.getDisplayName().isEmpty()) {
                formUserNameTextView.setText(userInfo.getDisplayName());
            } else if(userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
                formUserNameTextView.setText(userInfo.getUsername());
            } else {
                formUserNameTextView.setText(R.string.anonymous);
            }

            // Set user avatar
            if (userInfo.getAvatarSrc() != null && !userInfo.getAvatarSrc().isEmpty()) {
                AvatarFetcher.fetchTransformInto(getContext(), userInfo.getAvatarSrc(), formAvatarImageView);
            } else {
                AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, formAvatarImageView);
            }
        } else {
            // Default state for anonymous user
            formUserNameTextView.setText(R.string.anonymous);
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, formAvatarImageView);
        }
    }

    /**
     * Request the image picker to be shown
     */
    private void requestImagePicker() {
        // Check if we've reached maximum images
        if (selectedMediaUris.size() >= MAX_IMAGES) {
            showError(getContext().getString(R.string.max_images_reached));
            return;
        }

        // Notify listener to handle image picker
        if (listener != null) {
            listener.onImagePickerRequested();
        }
    }

    /**
     * Handle the result from the image picker
     *
     * @param imageUri URI of the selected image
     */
    public void handleImageResult(Uri imageUri) {
        if (imageUri != null) {
            // Add image to adapter
            mediaAdapter.addMedia(imageUri);
            selectedMediaUris.add(imageUri);

            // Show media preview container and update submit button
            if (mediaPreviewContainer.getVisibility() != VISIBLE) {
                mediaPreviewContainer.setVisibility(VISIBLE);
            }

            updateSubmitButtonState();
        }
    }

    /**
     * Add an image URI programmatically to the feed post
     * This method can be used by custom toolbar buttons to add images
     *
     * @param imageUri URI of the image to add
     * @return true if image was added successfully, false if max images reached or invalid URI
     */
    public boolean addImageUri(Uri imageUri) {
        if (imageUri == null) {
            return false;
        }

        // Check if we've reached maximum images (both local and remote)
        if (selectedMediaUris.size() + remoteMediaItems.size() >= MAX_IMAGES) {
            showError(getContext().getString(R.string.max_images_reached));
            return false;
        }

        String scheme = imageUri.getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            // Handle as remote media - add directly to remoteMediaItems
            FeedPostMediaItem mediaItem = new FeedPostMediaItem();
            FeedPostMediaItemAsset asset = new FeedPostMediaItemAsset()
                .src(imageUri.toString())
                .w(400)  // Default dimensions for GIFs
                .h(300);
            mediaItem.setSizes(java.util.Arrays.asList(asset));
            remoteMediaItems.add(mediaItem);

            // Add to adapter for UI preview
            mediaAdapter.addMedia(imageUri);
        } else {
            // Handle as local file - existing logic
            mediaAdapter.addMedia(imageUri);
            selectedMediaUris.add(imageUri);
        }

        // Show media preview container and update submit button
        if (mediaPreviewContainer.getVisibility() != VISIBLE) {
            mediaPreviewContainer.setVisibility(VISIBLE);
        }
        updateSubmitButtonState();

        return true;
    }

    /**
     * Remove a media item at the specified position
     *
     * @param position Position of the media item to remove
     */
    private void removeMedia(int position) {
        int totalMedia = selectedMediaUris.size() + remoteMediaItems.size();
        if (position >= 0 && position < totalMedia) {
            // Remove from UI adapter first
            mediaAdapter.removeMedia(position);

            // Determine if this is a local or remote item and remove from appropriate list
            if (position < selectedMediaUris.size()) {
                // It's a local URI
                selectedMediaUris.remove(position);
            } else {
                // It's a remote media item
                int remoteIndex = position - selectedMediaUris.size();
                if (remoteIndex < remoteMediaItems.size()) {
                    remoteMediaItems.remove(remoteIndex);
                }
            }

            // Hide media container if no media items left
            if (selectedMediaUris.isEmpty() && remoteMediaItems.isEmpty()) {
                mediaPreviewContainer.setVisibility(GONE);
            }

            updateSubmitButtonState();
        }
    }

    /**
     * Show the dialog to add a link
     */
    private void showAddLinkDialog() {
        // Don't show dialog if a link is already attached
        if (attachedLink != null) {
            Toast.makeText(getContext(),
                    "A link is already attached. Remove it first to add a new one.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AddLinkDialog dialog = new AddLinkDialog(getContext(), this::addLink);
        dialog.show();
    }

    /**
     * Add a link to the post
     *
     * @param link The link to add
     */
    private void addLink(FeedPostLink link) {
        if (link != null) {
            attachedLink = link;

            // Update link preview UI
            linkPreviewContainer.setVisibility(VISIBLE);

            if (link.getTitle() != null && !link.getTitle().isEmpty()) {
                linkTitleTextView.setText(link.getTitle());
                linkTitleTextView.setVisibility(VISIBLE);
            } else {
                linkTitleTextView.setVisibility(GONE);
            }

            linkUrlTextView.setText(link.getUrl());

            updateSubmitButtonState();
        }
    }

    /**
     * Remove the attached link
     */
    private void removeLink() {
        attachedLink = null;
        linkPreviewContainer.setVisibility(GONE);
        updateSubmitButtonState();
    }

    /**
     * Cancel post creation
     */
    private void cancelPost() {
        // Reset form
        postContentEditText.setText("");
        mediaAdapter.clearMedia();
        selectedMediaUris.clear();
        remoteMediaItems.clear();
        mediaPreviewContainer.setVisibility(GONE);
        removeLink();
        postErrorTextView.setVisibility(GONE);

        // Notify listener
        if (listener != null) {
            listener.onPostCreateCancelled();
        }
    }

    /**
     * Enable/disable submit button based on content
     */
    private void updateSubmitButtonState() {
        boolean hasContent = !TextUtils.isEmpty(postContentEditText.getText()) ||
                !selectedMediaUris.isEmpty() ||
                !remoteMediaItems.isEmpty() ||
                attachedLink != null;

        submitPostButton.setEnabled(hasContent);

        // Update button appearance based on state
        if (hasContent) {
            submitPostButton.setAlpha(1.0f);
            submitPostButton.setBackgroundColor(0xFF4285F4); // Blue
        } else {
            submitPostButton.setAlpha(0.5f);
            submitPostButton.setBackgroundColor(0xFF757575); // Gray
        }
    }

    /**
     * Show an error message
     *
     * @param errorMessage The error message to display
     */
    private void showError(String errorMessage) {
        postErrorTextView.setText(errorMessage);
        postErrorTextView.setVisibility(VISIBLE);
    }

    /**
     * Hide the error message
     */
    private void hideError() {
        postErrorTextView.setVisibility(GONE);
    }

    /**
     * Validate and submit the post
     */
    private void validateAndSubmitPost() {
        // Reset error state
        hideError();

        // Check that we have content
        String content = postContentEditText.getText() != null ?
                postContentEditText.getText().toString().trim() : "";

        boolean hasContent = !content.isEmpty() || !selectedMediaUris.isEmpty() || !remoteMediaItems.isEmpty() || attachedLink != null;

        if (!hasContent) {
            showError(getContext().getString(R.string.content_required));
            return;
        }

        // Show loading state
        setSubmitting(true);

        createPostFromInput(content, new FCCallback<FeedPost>() {
            @Override
            public boolean onFailure(APIError error) {
                Log.e("FeedPostCreateView", "Create post from input failed: " +
                    (error != null ? error.getReason() : "Unknown error") +
                    ", translated: " + (error != null ? error.getTranslatedError() : "None"));
                mainHandler.post(() -> {
                    // Hide loading state
                    setSubmitting(false);

                    // Show error message
                    String errorMessage = error != null && error.getTranslatedError() != null ?
                            error.getTranslatedError() : getContext().getString(R.string.post_error);

                    showError(errorMessage);

                    // Notify listener
                    if (listener != null) {
                        listener.onPostCreateError(errorMessage);
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(FeedPost feedPost) {
                final CreateFeedPostParams params = convertToParams(feedPost);

                if (sdk != null) {
                    sdk.createPost(params, new FCCallback<FeedPost>() {
                        @Override
                        public boolean onFailure(APIError error) {
                            Log.e("FeedPostCreateView", "Post creation failed: " +
                                (error != null ? error.getReason() : "Unknown error") +
                                ", translated: " + (error != null ? error.getTranslatedError() : "None"));
                            mainHandler.post(() -> {
                                // Hide loading state
                                setSubmitting(false);

                                // Show error message
                                String errorMessage = error != null && error.getTranslatedError() != null ?
                                        error.getTranslatedError() : getContext().getString(R.string.post_error);

                                showError(errorMessage);

                                // Notify listener
                                if (listener != null) {
                                    listener.onPostCreateError(errorMessage);
                                }
                            });
                            return CONSUME;
                        }

                        @Override
                        public boolean onSuccess(FeedPost createdPost) {
                            mainHandler.post(() -> {
                                // Reset form and hide loading
                                setSubmitting(false);

                                // Show success message and reset form
                                Toast.makeText(getContext(), R.string.post_success, Toast.LENGTH_SHORT).show();
                                resetForm();

                                // Notify listener
                                if (listener != null) {
                                    listener.onPostCreated(createdPost);
                                }
                            });
                            return CONSUME;
                        }
                    });
                } else {
                    // SDK not available, show error
                    setSubmitting(false);
                    showError(getContext().getString(R.string.post_error));

                    if (listener != null) {
                        listener.onPostCreateError(getContext().getString(R.string.post_error));
                    }
                }
                return CONSUME;
            }
        });
    }

    /**
     * Create a FeedPost object from the user input
     */
    private void createPostFromInput(String content, FCCallback<FeedPost> feedPostCallback) {
        FeedPost post = new FeedPost();

        // Set basic post information
        post.setContentHTML(content);

        // Set user information if available
        if (sdk.getCurrentUser() != null) {
            UserSessionInfo userInfo = sdk.getCurrentUser();
            post.setFromUserDisplayName(userInfo.getDisplayName());
            post.setFromUserAvatar(userInfo.getAvatarSrc());
        }

        // Add link if available
        if (attachedLink != null) {
            post.setLinks(Collections.singletonList(attachedLink));
        }

        // Handle media items (both local uploads and remote URLs)
        if (!selectedMediaUris.isEmpty()) {
            // Upload local images first
            sdk.uploadImages(getContext(), selectedMediaUris, new FCCallback<List<FeedPostMediaItem>>() {
                @Override
                public boolean onFailure(APIError error) {
                    Log.e("FeedPostCreateView", "Image upload failed: " +
                        (error != null ? error.getReason() : "Unknown error"));
                    mainHandler.post(() -> feedPostCallback.onFailure(error));
                    return CONSUME;
                }

                @Override
                public boolean onSuccess(List<FeedPostMediaItem> uploadedItems) {
                    // Combine uploaded items with remote media items
                    List<FeedPostMediaItem> allMediaItems = new ArrayList<>(uploadedItems);
                    allMediaItems.addAll(remoteMediaItems);
                    post.setMedia(allMediaItems);
                    mainHandler.post(() -> feedPostCallback.onSuccess(post));
                    return CONSUME;
                }
            });
        } else if (!remoteMediaItems.isEmpty()) {
            // Only remote media, no uploads needed
            post.setMedia(new ArrayList<>(remoteMediaItems));
            feedPostCallback.onSuccess(post);
        } else {
            // No media at all
            feedPostCallback.onSuccess(post);
        }
    }

    /**
     * Convert FeedPost to CreateFeedPostParams
     *
     * @param post The FeedPost to convert
     * @return A new CreateFeedPostParams object
     */
    private CreateFeedPostParams convertToParams(FeedPost post) {
        CreateFeedPostParams params = new CreateFeedPostParams();

        params.setTitle(post.getTitle());
        params.setContentHTML(post.getContentHTML());
        params.setMedia(post.getMedia());
        params.setLinks(post.getLinks());
        params.setFromUserId(post.getFromUserId());
        params.setFromUserDisplayName(post.getFromUserDisplayName());
        
        // Use tags from TagSupplier if available, otherwise use post's tags
        if (sdk != null && sdk.getTagSupplier() != null) {
            List<String> supplierTags = sdk.getTagSupplier().getTags(sdk.getCurrentUser());
            if (supplierTags != null && !supplierTags.isEmpty()) {
                params.setTags(supplierTags);
            } else {
                params.setTags(post.getTags());
            }
        } else {
            params.setTags(post.getTags());
        }
        
        params.setMeta(post.getMeta());

        return params;
    }

    /**
     * Reset the form
     */
    private void resetForm() {
        postContentEditText.setText("");
        mediaAdapter.clearMedia();
        selectedMediaUris.clear();
        remoteMediaItems.clear();
        mediaPreviewContainer.setVisibility(GONE);
        removeLink();
        hideError();
        updateSubmitButtonState();
    }

    /**
     * Set the submitting state (loading)
     *
     * @param submitting True if submitting, false otherwise
     */
    private void setSubmitting(boolean submitting) {
        postProgressBar.setVisibility(submitting ? VISIBLE : GONE);
        postContentEditText.setEnabled(!submitting);
        attachImageButton.setEnabled(!submitting);
        // attachLinkButton.setEnabled(!submitting); // Removed link button
        cancelPostButton.setEnabled(!submitting);
        submitPostButton.setEnabled(!submitting);

        // Disable remove buttons during submission
        removeLinkButton.setEnabled(!submitting);

        // Disable custom toolbar buttons during submission
        for (int i = 0; i < customToolbarContainer.getChildCount(); i++) {
            customToolbarContainer.getChildAt(i).setEnabled(!submitting);
        }
    }

    // Custom toolbar button management methods

    /**
     * Add a custom toolbar button to the feed post creation toolbar
     *
     * @param button The custom button to add
     */
    public void addCustomToolbarButton(FeedCustomToolbarButton button) {
        // Check if button already exists
        for (FeedCustomToolbarButton existingButton : customButtons) {
            if (existingButton.getId().equals(button.getId())) {
                return; // Button already exists
            }
        }

        customButtons.add(button);
        addToolbarButtonView(button);
        updateToolbarVisibility();
    }

    /**
     * Remove a custom toolbar button by ID
     *
     * @param buttonId The ID of the button to remove
     */
    public void removeCustomToolbarButton(String buttonId) {
        FeedCustomToolbarButton buttonToRemove = null;
        for (FeedCustomToolbarButton button : customButtons) {
            if (button.getId().equals(buttonId)) {
                buttonToRemove = button;
                break;
            }
        }

        if (buttonToRemove != null) {
            customButtons.remove(buttonToRemove);
            rebuildToolbar();
        }
    }

    /**
     * Clear all custom toolbar buttons
     */
    public void clearCustomToolbarButtons() {
        customButtons.clear();
        rebuildToolbar();
    }

    /**
     * Rebuild the toolbar with current buttons
     */
    private void rebuildToolbar() {
        // Clear existing buttons
        customToolbarContainer.removeAllViews();

        // Add current buttons
        for (FeedCustomToolbarButton button : customButtons) {
            addToolbarButtonView(button);
        }

        updateToolbarVisibility();
    }

    /**
     * Add a custom toolbar button view
     */
    private void addToolbarButtonView(FeedCustomToolbarButton customButton) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(customButton.getIconResourceId());

        if (customButton.getContentDescriptionResourceId() != 0) {
            button.setContentDescription(getContext().getString(customButton.getContentDescriptionResourceId()));
        }

        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        button.setBackgroundResource(outValue.resourceId);
        button.setOnClickListener(v -> customButton.onClick(this, v));

        // Ensure icon scales properly within button bounds
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = (int) (4 * getContext().getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding, padding, padding);

        // Apply theme colors
        FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
        int actionButtonColor = ThemeColorResolver.getActionButtonColor(getContext(), theme);
        button.setImageTintList(ColorStateList.valueOf(actionButtonColor));

        // Set consistent size to match image button (40dp)
        int size = (int) (40 * getContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(4, 0, 4, 0);
        button.setLayoutParams(params);

        // Update button state
        button.setEnabled(customButton.isEnabled(this));
        button.setVisibility(customButton.isVisible(this) ? View.VISIBLE : View.GONE);

        customToolbarContainer.addView(button);
        customButton.onAttached(this, button);
    }

    /**
     * Update toolbar visibility based on whether there are custom buttons
     */
    private void updateToolbarVisibility() {
        customToolbarScrollView.setVisibility(customButtons.isEmpty() ? GONE : VISIBLE);
    }

    // Text manipulation methods for custom buttons

    /**
     * Insert text at the current cursor position in the post content
     *
     * @param text The text to insert
     */
    public void insertTextAtCursor(String text) {
        if (postContentEditText != null) {
            int start = postContentEditText.getSelectionStart();
            int end = postContentEditText.getSelectionEnd();
            Editable editable = postContentEditText.getText();
            editable.replace(Math.min(start, end), Math.max(start, end), text);
        }
    }

    /**
     * Get the currently selected text
     *
     * @return The selected text, or null if no selection
     */
    public String getSelectedText() {
        if (postContentEditText != null) {
            int start = postContentEditText.getSelectionStart();
            int end = postContentEditText.getSelectionEnd();
            if (start != end) {
                return postContentEditText.getText().subSequence(start, end).toString();
            }
        }
        return null;
    }

    /**
     * Replace the selected text with new text
     *
     * @param text The text to replace the selection with
     */
    public void replaceSelection(String text) {
        if (postContentEditText != null) {
            int start = postContentEditText.getSelectionStart();
            int end = postContentEditText.getSelectionEnd();
            Editable editable = postContentEditText.getText();
            editable.replace(start, end, text);
        }
    }

    /**
     * Request focus on the post content input
     */
    public void requestInputFocus() {
        if (postContentEditText != null) {
            postContentEditText.requestFocus();
        }
    }
}