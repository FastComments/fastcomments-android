package com.fastcomments.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

    // State variables
    private final List<Uri> selectedMediaUris = new ArrayList<>();
    private SelectedMediaAdapter mediaAdapter;
    private FeedPostLink attachedLink;
    private final Handler mainHandler;
    private FastCommentsFeedSDK sdk;
    private OnPostCreateListener listener;

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
     * Remove a media item at the specified position
     *
     * @param position Position of the media item to remove
     */
    private void removeMedia(int position) {
        if (position >= 0 && position < selectedMediaUris.size()) {
            selectedMediaUris.remove(position);
            mediaAdapter.removeMedia(position);

            // Hide media container if no media items left
            if (selectedMediaUris.isEmpty()) {
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

        boolean hasContent = !content.isEmpty() || !selectedMediaUris.isEmpty() || attachedLink != null;

        if (!hasContent) {
            showError(getContext().getString(R.string.content_required));
            return;
        }

        // Show loading state
        setSubmitting(true);

        createPostFromInput(content, new FCCallback<FeedPost>() {
            @Override
            public boolean onFailure(APIError error) {
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

        // Upload media items and attach them to the post
        if (!selectedMediaUris.isEmpty()) {
            sdk.uploadImages(getContext(), selectedMediaUris, new FCCallback<List<FeedPostMediaItem>>() {
                @Override
                public boolean onFailure(APIError error) {
                    mainHandler.post(() -> feedPostCallback.onFailure(error));
                    return CONSUME;
                }

                @Override
                public boolean onSuccess(List<FeedPostMediaItem> response) {
                    post.setMedia(response);
                    mainHandler.post(() -> feedPostCallback.onSuccess(post));
                    return CONSUME;
                }
            });
        } else {
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
        params.setTags(post.getTags());
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
    }
}