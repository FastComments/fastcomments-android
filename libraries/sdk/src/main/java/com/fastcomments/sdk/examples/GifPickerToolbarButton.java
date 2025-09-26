package com.fastcomments.sdk.examples;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that demonstrates how to implement a GIF picker.
 * This is a simplified example that shows the concept - in a real implementation,
 * you would integrate with a GIF service like GIPHY, Tenor, etc.
 */
public class GifPickerToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "gif_picker";

    @Override
    public int getIconResourceId() {
        // You would create your own GIF icon drawable
        return android.R.drawable.ic_menu_gallery;
    }

    @Override
    public int getContentDescriptionResourceId() {
        // You would add this string to your strings.xml
        return R.string.add_gif; // "Add GIF"
    }

    @Override
    public String getBadgeText() {
        return null; // No badge for this button
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        showGifPicker(view, buttonView.getContext());
    }

    @Override
    public String getId() {
        return BUTTON_ID;
    }

    @Override
    public boolean isEnabled(BottomCommentInputView view) {
        return true; // Always enabled
    }

    @Override
    public boolean isVisible(BottomCommentInputView view) {
        return true; // Always visible
    }

    /**
     * Show a simple GIF picker dialog.
     * In a real implementation, this would open a GIF selection interface.
     */
    private void showGifPicker(BottomCommentInputView inputView, Context context) {
        // This is a simplified example - normally you'd integrate with GIPHY, Tenor, etc.
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Add GIF");
        builder.setMessage("In a real implementation, this would open a GIF picker interface.");

        // For demonstration, let's show a simple URL input
        EditText gifUrlInput = new EditText(context);
        gifUrlInput.setHint("Enter GIF URL (for demo)");
        gifUrlInput.setText("https://media.giphy.com/media/example/giphy.gif");
        builder.setView(gifUrlInput);

        builder.setPositiveButton("Insert GIF", (dialog, which) -> {
            String gifUrl = gifUrlInput.getText().toString().trim();
            if (!gifUrl.isEmpty()) {
                // Insert GIF as an image tag at cursor position
                String gifHtml = "<img src=\"" + gifUrl + "\" alt=\"GIF\" style=\"max-width: 200px;\" />";
                inputView.insertHtmlAtCursor(gifHtml);

                Toast.makeText(context, "GIF inserted!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);

        builder.setNeutralButton("Pick Random", (dialog, which) -> {
            // Insert a placeholder GIF for demonstration
            String demoGif = "<img src=\"https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif\" alt=\"Demo GIF\" style=\"max-width: 200px;\" />";
            inputView.insertHtmlAtCursor(demoGif);
            Toast.makeText(context, "Demo GIF inserted!", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }
}