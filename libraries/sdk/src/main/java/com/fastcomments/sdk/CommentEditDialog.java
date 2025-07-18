package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import java.util.Objects;

/**
 * Dialog to edit a comment
 */
public class CommentEditDialog {

    private Dialog dialog;
    private final Context context;
    private final FastCommentsSDK sdk;
    private Callback<String> onSaveCallback;

    public CommentEditDialog(Context context, FastCommentsSDK sdk) {
        this.context = context;
        this.sdk = sdk;
    }

    /**
     * Set the callback to be called when the edit is saved
     *
     * @param callback The callback to call with the new comment text
     * @return This dialog for chaining
     */
    public CommentEditDialog setOnSaveCallback(Callback<String> callback) {
        this.onSaveCallback = callback;
        return this;
    }

    /**
     * Show the edit dialog
     *
     * @param currentText The current comment text to edit
     */
    public void show(String currentText) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        
        View view = LayoutInflater.from(context).inflate(R.layout.edit_comment_dialog, null);
        dialog.setContentView(view);
        
        // Set up dialog views
        EditText editCommentText = view.findViewById(R.id.editCommentText);
        Button cancelButton = view.findViewById(R.id.cancelEditButton);
        Button saveButton = view.findViewById(R.id.saveEditButton);
        
        // Set initial comment text
        if (currentText != null) {
            // Remove any HTML tags from the comment text
            String plainText = android.text.Html.fromHtml(currentText, 
                    android.text.Html.FROM_HTML_MODE_COMPACT).toString();
            editCommentText.setText(plainText);
        }
        
        // Set cancel button action
        cancelButton.setOnClickListener(v -> dismiss());
        
        // Set save button action
        saveButton.setOnClickListener(v -> {
            String newText = editCommentText.getText().toString().trim();
            if (!newText.isEmpty() && onSaveCallback != null) {
                onSaveCallback.call(newText);
                dismiss();
            }
        });
        
        // Apply theme colors
        applyTheme(cancelButton, saveButton);
        
        dialog.show();
    }
    
    /**
     * Apply theme colors to dialog buttons
     */
    private void applyTheme(Button cancelButton, Button saveButton) {
        FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
        
        // Apply action button color to both buttons
        int actionButtonColor = ThemeColorResolver.getActionButtonColor(context, theme);
        if (cancelButton != null) {
            cancelButton.setTextColor(actionButtonColor);
        }
        if (saveButton != null) {
            saveButton.setTextColor(actionButtonColor);
        }
    }
    
    /**
     * Dismiss the dialog
     */
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}