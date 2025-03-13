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

import com.fastcomments.client.R;
import com.fastcomments.model.UserSessionInfo;

public class CommentFormView extends LinearLayout {

    private ImageView avatarImageView;
    private TextView userNameTextView;
    private EditText commentEditText;
    private Button submitButton;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private OnCommentSubmitListener submitListener;

    public interface OnCommentSubmitListener {
        void onCommentSubmit(String commentText);
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
        
        // Hide error text initially
        errorTextView.setVisibility(View.GONE);
        
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
                submitListener.onCommentSubmit(commentText);
            }
        });
    }
    
    /**
     * Set the listener for comment submission
     * @param listener OnCommentSubmitListener
     */
    public void setOnCommentSubmitListener(OnCommentSubmitListener listener) {
        this.submitListener = listener;
    }
    
    /**
     * Set the current user info in the form
     * @param userInfo UserSessionInfo
     */
    public void setCurrentUser(UserSessionInfo userInfo) {
        if (userInfo != null) {
            userNameTextView.setText(userInfo.getUserName());
            // Load avatar image if available
            // For now, use the default avatar
            avatarImageView.setImageResource(R.drawable.default_avatar);
        } else {
            userNameTextView.setText(R.string.anonymous);
            avatarImageView.setImageResource(R.drawable.default_avatar);
        }
    }
    
    /**
     * Show loading state during comment submission
     * @param submitting true to show loading, false to hide
     */
    public void setSubmitting(boolean submitting) {
        progressBar.setVisibility(submitting ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!submitting);
        commentEditText.setEnabled(!submitting);
    }
    
    /**
     * Clear the comment text input
     */
    public void clearText() {
        commentEditText.setText("");
    }
    
    /**
     * Show an error message
     * @param errorMessage Error message to display
     */
    public void showError(String errorMessage) {
        errorTextView.setText(errorMessage);
        errorTextView.setVisibility(View.VISIBLE);
    }
}