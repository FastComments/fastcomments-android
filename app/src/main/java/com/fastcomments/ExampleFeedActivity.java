package com.fastcomments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.FeedPost;
import com.fastcomments.sdk.FastCommentsFeedSDK;
import com.fastcomments.sdk.FastCommentsFeedView;

import java.util.List;

/**
 * Example activity showing how to use the FastCommentsFeedView
 */
public class ExampleFeedActivity extends AppCompatActivity {

    private FastCommentsFeedView feedView;
    private FastCommentsFeedSDK feedSDK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_feed);

        // Create a configuration for the SDK
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = "demo"; // Use your tenant ID here
        config.urlId = "https://example.com/page1"; // Use your URL ID here

        // Initialize the Feed SDK
        feedSDK = new FastCommentsFeedSDK(config);

        // Find the feed view in the layout
        feedView = findViewById(R.id.feedView);
        
        // Set the SDK instance for the view
        feedView.setSDK(feedSDK);

        // Set interaction listener
        feedView.setFeedViewInteractionListener(new FastCommentsFeedView.OnFeedViewInteractionListener() {
            @Override
            public void onFeedLoaded(List<FeedPost> posts) {
                // Feed loaded successfully
            }

            @Override
            public void onFeedError(String errorMessage) {
                // Error loading feed
                // Consumer can handle this error as needed
            }

            @Override
            public void onPostSelected(FeedPost post) {
                // User selected a post
                // Here you would typically navigate to a detail view 
                // or show comments for this post
                
                // In a real app, you might do:
                // navigateToPostDetail(post);
                // or
                // showCommentsForPost(post);
            }
        });

        // Load the feed
        feedView.load();
    }
}