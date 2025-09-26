package com.fastcomments.sdk.examples;

import android.net.Uri;
import android.view.View;

import com.fastcomments.sdk.FeedCustomToolbarButton;
import com.fastcomments.sdk.FeedPostCreateView;
import com.fastcomments.sdk.R;

import java.util.Random;

/**
 * Example custom feed toolbar button that demonstrates how to add a random GIF.
 * This button immediately inserts a random GIF from a curated list without prompting the user.
 */
public class GifPickerFeedToolbarButton implements FeedCustomToolbarButton {

    private static final String BUTTON_ID = "gif_picker_feed";
    private static final Random random = new Random();

    // Working GIF URLs for testing
    private static final String[] GIF_URLS = {
        "https://media0.giphy.com/media/QgcQLZa6glP2w/200.gif",
        "https://media0.giphy.com/media/feqkVgjJpYtjy/200.gif",
        "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif",
        "https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif"
    };

    @Override
    public int getIconResourceId() {
        return android.R.drawable.btn_star;
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
    public void onClick(FeedPostCreateView view, View buttonView) {
        // Pick a random GIF from our curated list
        String randomGifUrl = GIF_URLS[random.nextInt(GIF_URLS.length)];

        // Create URI and add it directly to the post
        Uri gifUri = Uri.parse(randomGifUrl);
        boolean success = view.addImageUri(gifUri);

        // Note: In your app, you might want to show a toast on failure
        // but for this demo we keep it simple
    }

    @Override
    public String getId() {
        return BUTTON_ID;
    }
}