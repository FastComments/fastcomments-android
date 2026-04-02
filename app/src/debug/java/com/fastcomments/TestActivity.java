package com.fastcomments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.SortDirections;
import com.fastcomments.sdk.FastCommentsSDK;
import com.fastcomments.sdk.FastCommentsView;

/**
 * Debug-only activity used by dual-emulator UI tests.
 * Reads tenantId, urlId, and sso token from intent extras,
 * creates a FastCommentsView, and loads comments.
 */
public class TestActivity extends AppCompatActivity {

    private FastCommentsView commentsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        String tenantId = getIntent().getStringExtra("tenantId");
        String urlId = getIntent().getStringExtra("urlId");
        String ssoToken = getIntent().getStringExtra("sso");

        CommentWidgetConfig config = new CommentWidgetConfig(tenantId, urlId);
        config.sso = ssoToken;
        config.showLiveRightAway = true;
        config.defaultSortDirection = SortDirections.NF;

        FastCommentsSDK sdk = new FastCommentsSDK(config, true);

        commentsView = findViewById(R.id.commentsView);
        commentsView.setSDK(sdk);
        commentsView.load();
    }

    @Override
    protected void onDestroy() {
        if (commentsView != null) {
            commentsView.cleanup();
        }
        super.onDestroy();
    }
}
