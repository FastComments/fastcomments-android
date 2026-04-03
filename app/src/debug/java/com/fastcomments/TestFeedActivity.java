package com.fastcomments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.sdk.FastCommentsFeedSDK;
import com.fastcomments.sdk.FastCommentsFeedView;

/**
 * Debug-only activity used by dual-emulator feed UI tests.
 * Reads tenantId, urlId, and sso token from intent extras,
 * creates a FastCommentsFeedView, and loads the feed.
 *
 * Exposes feedSDK publicly so tests can call createPost() directly.
 */
public class TestFeedActivity extends AppCompatActivity {

    public FastCommentsFeedSDK feedSDK;
    private FastCommentsFeedView feedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_feed);

        String tenantId = getIntent().getStringExtra("tenantId");
        String urlId = getIntent().getStringExtra("urlId");
        String ssoToken = getIntent().getStringExtra("sso");

        CommentWidgetConfig config = new CommentWidgetConfig(tenantId, urlId);
        config.sso = ssoToken;

        feedSDK = new FastCommentsFeedSDK(config);

        feedView = findViewById(R.id.feedView);
        feedView.setSDK(feedSDK);
        feedView.load();
    }

    @Override
    protected void onDestroy() {
        if (feedView != null) {
            feedView.cleanup();
        }
        super.onDestroy();
    }
}
