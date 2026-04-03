package com.fastcomments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.SortDirections;
import com.fastcomments.sdk.FastCommentsSDK;
import com.fastcomments.sdk.LiveChatView;

/**
 * Debug-only activity used by dual-emulator live chat UI tests.
 * Reads tenantId, urlId, and sso token from intent extras,
 * creates a LiveChatView, and loads messages.
 */
public class TestLiveChatActivity extends AppCompatActivity {

    private LiveChatView liveChatView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_live_chat);

        String tenantId = getIntent().getStringExtra("tenantId");
        String urlId = getIntent().getStringExtra("urlId");
        String ssoToken = getIntent().getStringExtra("sso");

        CommentWidgetConfig config = new CommentWidgetConfig(tenantId, urlId);
        config.sso = ssoToken;

        FastCommentsSDK sdk = new FastCommentsSDK(config, true);

        liveChatView = findViewById(R.id.liveChatView);
        liveChatView.setSDK(sdk);
        liveChatView.load();
    }

    @Override
    protected void onDestroy() {
        if (liveChatView != null) {
            liveChatView.cleanup();
        }
        super.onDestroy();
    }
}
