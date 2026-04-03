package com.fastcomments;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.FeedPost;
import com.fastcomments.sdk.FastCommentsFeedSDK;
import com.fastcomments.sdk.FastCommentsFeedView;
import com.fastcomments.sdk.FeedPostCreateView;

/**
 * Debug-only activity used by dual-emulator feed UI tests.
 * Includes a FeedPostCreateView for posting and a FastCommentsFeedView for viewing.
 */
public class TestFeedActivity extends AppCompatActivity {

    public FastCommentsFeedSDK feedSDK;
    private FastCommentsFeedView feedView;
    private FeedPostCreateView createView;

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

        createView = findViewById(R.id.feedPostCreateView);
        createView.setSDK(feedSDK);
        createView.setOnPostCreateListener(new FeedPostCreateView.OnPostCreateListener() {
            @Override
            public void onPostCreated(FeedPost post) {
                feedView.refresh();
            }

            @Override
            public void onPostCreateError(String errorMessage) {
                Log.e("TestFeedActivity", "Post create error: " + errorMessage);
            }

            @Override
            public void onPostCreateCancelled() {}

            @Override
            public void onImagePickerRequested() {}
        });
    }

    @Override
    protected void onDestroy() {
        if (feedView != null) {
            feedView.cleanup();
        }
        super.onDestroy();
    }
}
