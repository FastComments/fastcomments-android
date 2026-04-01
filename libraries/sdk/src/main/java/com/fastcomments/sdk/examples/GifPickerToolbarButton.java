package com.fastcomments.sdk.examples;

import android.view.View;
import android.widget.Toast;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that inserts a random GIF.
 * In a real implementation, this would open a GIF picker (e.g. GIPHY, Tenor).
 */
public class GifPickerToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "gif_picker";

    private static final String[] DEMO_GIFS = {
            "https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif",
            "https://media.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif",
            "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif",
    };

    private int gifIndex = 0;

    @Override
    public int getIconResourceId() {
        return R.drawable.fastcomments_ic_gif_text;
    }

    @Override
    public int getContentDescriptionResourceId() {
        return R.string.add_gif;
    }

    @Override
    public String getBadgeText() {
        return null;
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        String gifUrl = DEMO_GIFS[gifIndex % DEMO_GIFS.length];
        gifIndex++;
        String gifHtml = "<img src=\"" + gifUrl + "\" alt=\"GIF\" style=\"max-width: 200px;\" />";
        view.insertHtmlAtCursor(gifHtml);
        Toast.makeText(buttonView.getContext(), "GIF inserted!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public String getId() {
        return BUTTON_ID;
    }
}
