package com.fastcomments.sdk.examples;

import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.ScrollView;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that demonstrates how to implement an emoji picker.
 * This shows a simple grid of common emojis that can be inserted into comments.
 */
public class EmojiPickerToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "emoji_picker";

    // Common emojis to display in the picker
    private static final String[] EMOJIS = {
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
        "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
        "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
        "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥",
        "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
        "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
        "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
        "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻",
        "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👋",
        "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇",
        "☝️", "👍", "👎", "👊", "✊", "🤛", "🤜", "👏",
        "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
        "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
        "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
        "👄", "💋", "🩸", "👶", "🧒", "👦", "👧", "🧑",
        "👱", "👨", "🧔", "👨‍🦰", "👨‍🦱", "👨‍🦳", "👨‍🦲", "👩",
        "👩‍🦰", "🧑‍🦰", "👩‍🦱", "🧑‍🦱", "👩‍🦳", "🧑‍🦳", "👩‍🦲", "🧑‍🦲",
        "👱‍♀️", "👱‍♂️", "🧓", "👴", "👵", "🙍", "🙍‍♂️", "🙍‍♀️",
        "🙎", "🙎‍♂️", "🙎‍♀️", "🙅", "🙅‍♂️", "🙅‍♀️", "🙆", "🙆‍♂️"
    };

    @Override
    public int getIconResourceId() {
        return android.R.drawable.btn_star;
    }

    @Override
    public int getContentDescriptionResourceId() {
        // You would add this string to your strings.xml
        return R.string.add_emoji; // "Add Emoji"
    }

    @Override
    public String getBadgeText() {
        return null; // No badge for this button
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        showEmojiPicker(view, buttonView.getContext());
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
     * Show an emoji picker dialog with a grid of emojis
     */
    private void showEmojiPicker(BottomCommentInputView inputView, Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Pick an Emoji");

        // Create a scrollable grid of emojis
        ScrollView scrollView = new ScrollView(context);
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setColumnCount(8); // 8 emojis per row
        gridLayout.setPadding(20, 20, 20, 20);

        // Add each emoji as a clickable TextView
        for (String emoji : EMOJIS) {
            TextView emojiView = new TextView(context);
            emojiView.setText(emoji);
            emojiView.setTextSize(24);
            emojiView.setPadding(16, 16, 16, 16);
            emojiView.setClickable(true);
            emojiView.setFocusable(true);

            // Set background to show selection feedback
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            emojiView.setBackgroundResource(outValue.resourceId);

            emojiView.setOnClickListener(v -> {
                // Insert the emoji at cursor position
                inputView.insertTextAtCursor(emoji + " ");
                // Dialog will be closed by the onClick handler set later
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(4, 4, 4, 4);
            emojiView.setLayoutParams(params);

            gridLayout.addView(emojiView);
        }

        scrollView.addView(gridLayout);
        builder.setView(scrollView);

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        // Set up click listeners to close dialog when emoji is selected
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            View child = gridLayout.getChildAt(i);
            if (child instanceof TextView) {
                TextView emojiView = (TextView) child;
                String emoji = emojiView.getText().toString();
                emojiView.setOnClickListener(v -> {
                    inputView.insertTextAtCursor(emoji + " ");
                    dialog.dismiss();
                });
            }
        }

        dialog.show();
    }
}