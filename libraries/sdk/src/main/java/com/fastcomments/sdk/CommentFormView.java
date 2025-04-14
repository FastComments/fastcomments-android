package com.fastcomments.sdk;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fastcomments.model.PublicComment;
import com.fastcomments.model.UserSessionInfo;

public class CommentFormView extends LinearLayout {

    private ImageView avatarImageView;
    private TextView userNameTextView;
    private EditText commentEditText;
    private Button submitButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private TextView replyingToTextView;
    private OnCommentSubmitListener submitListener;
    private OnCancelReplyListener cancelListener;
    private String parentId;

    public interface OnCommentSubmitListener {
        void onCommentSubmit(String commentText, String parentId);
    }

    public interface OnCancelReplyListener {
        void onCancelReply();
    }

    public CommentFormView(Context context) {
        super(context);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.comment_form_view, this, true);

        avatarImageView = findViewById(R.id.formAvatar);
        userNameTextView = findViewById(R.id.formUserName);
        commentEditText = findViewById(R.id.formEditText);
        submitButton = findViewById(R.id.formSubmitButton);
        progressBar = findViewById(R.id.formProgressBar);
        errorTextView = findViewById(R.id.formErrorText);
        replyingToTextView = findViewById(R.id.replyingToText);
        cancelButton = findViewById(R.id.cancelReplyButton);

        // Hide error text initially
        errorTextView.setVisibility(View.GONE);
        replyingToTextView.setVisibility(View.GONE);

        // Set up the submit button
        submitButton.setOnClickListener(v -> {
            String commentText = commentEditText.getText().toString().trim();
            if (TextUtils.isEmpty(commentText)) {
                errorTextView.setText(R.string.empty_comment_error);
                errorTextView.setVisibility(View.VISIBLE);
                return;
            }

            errorTextView.setVisibility(View.GONE);
            if (submitListener != null) {
                submitListener.onCommentSubmit(commentText, parentId);
            }
        });

        // Set up cancel button
        cancelButton.setOnClickListener(v -> {
            if (cancelListener != null) {
                cancelListener.onCancelReply();
            }
            resetReplyState();
        });

        // Initially hide cancel button until replying
        cancelButton.setVisibility(View.GONE);
    }

    /**
     * Set the listener for comment submission
     *
     * @param listener OnCommentSubmitListener
     */
    public void setOnCommentSubmitListener(OnCommentSubmitListener listener) {
        this.submitListener = listener;
    }

    /**
     * Set the listener for canceling a reply
     *
     * @param listener OnCancelReplyListener
     */
    public void setOnCancelReplyListener(OnCancelReplyListener listener) {
        this.cancelListener = listener;
    }

    /**
     * Set the current user info in the form
     *
     * @param userInfo UserSessionInfo
     */
    public void setCurrentUser(@NonNull UserSessionInfo userInfo) {
        // Try displayName first, then fallback to username, then Anonymous
        String nameToShow = userInfo.getDisplayName();
        if (nameToShow == null || nameToShow.isEmpty()) {
            nameToShow = userInfo.getUsername();
            if (nameToShow == null || nameToShow.isEmpty()) {
                nameToShow = getContext().getString(R.string.anonymous);
            }
        }
        userNameTextView.setText(nameToShow);
        
        if (userInfo.getAvatarSrc() != null) {
            AvatarFetcher.fetchTransformInto(getContext(), userInfo.getAvatarSrc(), avatarImageView);
        } else {
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, avatarImageView);
        }
    }

    /**
     * Show loading state during comment submission
     *
     * @param submitting true to show loading, false to hide
     */
    public void setSubmitting(boolean submitting) {
        progressBar.setVisibility(submitting ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!submitting);
        commentEditText.setEnabled(!submitting);
        cancelButton.setEnabled(!submitting);
    }

    /**
     * Clear the comment text input
     */
    public void clearText() {
        commentEditText.setText("");
    }

    /**
     * Show an error message
     *
     * @param errorMessage Error message to display
     */
    public void showError(String errorMessage) {
        errorTextView.setText(errorMessage);
        errorTextView.setVisibility(View.VISIBLE);
    }

    /**
     * Set up the form for replying to a comment.
     */
    public void setReplyingTo(RenderableComment renderableComment) {
        if (renderableComment != null) {
            final PublicComment comment = renderableComment.getComment();
            this.parentId = comment.getId();
            String commenterName = comment.getCommenterName() != null
                    ? comment.getCommenterName()
                    : getContext().getString(R.string.anonymous);

            replyingToTextView.setText(getContext().getString(R.string.replying_to, commenterName));
            replyingToTextView.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.VISIBLE);

            // Set hint to indicate replying
            commentEditText.setHint(R.string.reply_hint);

            // Focus the comment box
            commentEditText.requestFocus();
        }
    }

    /**
     * Reset to top-level comment state (not replying)
     */
    public void resetReplyState() {
        this.parentId = null;
        replyingToTextView.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        commentEditText.setHint(R.string.comment_hint);
        clearText();
    }
}