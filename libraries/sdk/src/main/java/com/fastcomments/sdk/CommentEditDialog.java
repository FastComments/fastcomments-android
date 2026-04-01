package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

/**
 * Dialog to edit a comment.
 * Loads existing HTML as WYSIWYG Spanned text and serializes back to HTML on save.
 */
public class CommentEditDialog {

    private Dialog dialog;
    private final Context context;
    private final FastCommentsSDK sdk;
    private Callback<String> onSaveCallback;
    private RichTextHelper.EditableImageGetter editableImageGetter;

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
     * @param currentText The current comment text to edit (HTML)
     */
    public void show(String currentText) {
        // Clean up any previous dialog state
        if (editableImageGetter != null) {
            editableImageGetter.clearTargets();
            editableImageGetter = null;
        }
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        View view = LayoutInflater.from(context).inflate(R.layout.edit_comment_dialog, null);
        dialog.setContentView(view);

        // Set up dialog views
        EditText editCommentText = view.findViewById(R.id.editCommentText);
        Button cancelButton = view.findViewById(R.id.cancelEditButton);
        Button saveButton = view.findViewById(R.id.saveEditButton);

        // Load HTML as WYSIWYG Spanned text (preserves bold, italic, code, links)
        if (currentText != null) {
            RichTextHelper.FromHtmlResult result = RichTextHelper.fromHtml(currentText, editCommentText);
            editableImageGetter = result.imageGetter;
            editCommentText.setText(result.spanned);
        }

        // Set cancel button action
        cancelButton.setOnClickListener(v -> dismiss());

        // Set save button action — serialize spans back to HTML
        saveButton.setOnClickListener(v -> {
            String plainText = editCommentText.getText().toString().trim();
            if (!plainText.isEmpty() && onSaveCallback != null) {
                String html = RichTextHelper.toHtml(editCommentText.getText()).trim();
                onSaveCallback.call(html);
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
     * Dismiss the dialog and clean up Glide targets
     */
    public void dismiss() {
        if (editableImageGetter != null) {
            editableImageGetter.clearTargets();
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
