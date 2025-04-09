package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;

import com.fastcomments.model.FeedPostLink;

/**
 * Dialog for adding a link to a post
 */
public class AddLinkDialog extends Dialog {

    private TextInputEditText linkUrlEditText;
    private TextInputEditText linkTitleEditText;
    private TextInputEditText linkDescriptionEditText;
    private TextView linkErrorTextView;
    private final OnLinkAddedListener listener;
    private Button cancelButton;
    private Button addButton;

    /**
     * Interface for notifying when a link is added
     */
    public interface OnLinkAddedListener {
        void onLinkAdded(FeedPostLink link);
    }

    public AddLinkDialog(@NonNull Context context, OnLinkAddedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.dialog_add_link);
        
        // Find views
        linkUrlEditText = findViewById(R.id.linkUrlEditText);
        linkTitleEditText = findViewById(R.id.linkTitleEditText);
        linkDescriptionEditText = findViewById(R.id.linkDescriptionEditText);
        linkErrorTextView = findViewById(R.id.linkErrorTextView);
        
        // Find the buttons in the layout
        cancelButton = findViewById(android.R.id.button2);  // Negative button
        addButton = findViewById(android.R.id.button1);     // Positive button
        
        // Set button click listeners
        cancelButton.setOnClickListener(v -> dismiss());
        addButton.setOnClickListener(v -> validateAndAddLink());
        
        // Set dialog width
        if (getWindow() != null) {
            getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Validate link inputs and add the link if valid
     */
    private void validateAndAddLink() {
        // Reset error state
        linkErrorTextView.setVisibility(View.GONE);
        
        // Get input values
        String url = linkUrlEditText.getText() != null ? linkUrlEditText.getText().toString().trim() : "";
        String title = linkTitleEditText.getText() != null ? linkTitleEditText.getText().toString().trim() : "";
        String description = linkDescriptionEditText.getText() != null ? linkDescriptionEditText.getText().toString().trim() : "";
        
        // Validate URL
        if (url.isEmpty()) {
            showError(getContext().getString(R.string.link_url_required));
            return;
        }
        
        // Ensure URL is valid
        if (!URLUtil.isValidUrl(url) && !url.startsWith("http://") && !url.startsWith("https://")) {
            // Try adding https:// prefix if missing
            url = "https://" + url;
            
            // Check if it's valid now
            if (!URLUtil.isValidUrl(url)) {
                showError(getContext().getString(R.string.invalid_url));
                return;
            }
        }
        
        // Create the link object
        FeedPostLink link = new FeedPostLink();
        link.setUrl(url);
        
        if (!title.isEmpty()) {
            link.setTitle(title);
        }
        
        if (!description.isEmpty()) {
            link.setDescription(description);
        }
        
        // Notify listener and close dialog
        if (listener != null) {
            listener.onLinkAdded(link);
        }
        
        dismiss();
    }
    
    /**
     * Show an error message
     * 
     * @param errorMessage The error message to display
     */
    private void showError(String errorMessage) {
        linkErrorTextView.setText(errorMessage);
        linkErrorTextView.setVisibility(View.VISIBLE);
    }
}