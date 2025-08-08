package com.fastcomments;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.core.sso.FastCommentsSSO;
import com.fastcomments.core.sso.SimpleSSOUserData;
import com.fastcomments.model.FeedPost;
import com.fastcomments.sdk.CommentsDialog;
import com.fastcomments.sdk.FastCommentsFeedSDK;
import com.fastcomments.sdk.FastCommentsFeedView;
import com.fastcomments.sdk.FeedPostCreateView;
import com.fastcomments.sdk.OnUserClickListener;
import com.fastcomments.sdk.TagSupplier;
import com.fastcomments.sdk.UserClickContext;
import com.fastcomments.sdk.UserClickSource;
import com.fastcomments.sdk.UserInfo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.List;

/**
 * Example activity showing how to use the FastCommentsFeedView
 */
public class FeedExampleActivity extends AppCompatActivity {

    private FastCommentsFeedView feedView;
    private FastCommentsFeedSDK feedSDK;
    private FeedPostCreateView postCreateView;
    private FloatingActionButton createPostFab;
    private OnUserClickListener userClickListener;

    // Image picker launcher
    private final ActivityResultLauncher<String> pickImageLauncher = 
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && postCreateView != null) {
                    postCreateView.handleImageResult(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_feed);

        // Create a configuration for the SDK
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = "demo"; // Use your tenant ID here
        config.urlId = "https://example.com/page1"; // Use your URL ID here
        config.pageTitle = "Feed Example";
        
        // Set up Simple SSO for user authentication. In production you probably want to use SecureSSO and create a token from your server.
        SimpleSSOUserData userData = new SimpleSSOUserData(
                "Example User", 
                "user@example.com", 
                "https://staticm.fastcomments.com/1639362726066-DSC_0841.JPG");
        
        FastCommentsSSO sso = new FastCommentsSSO(userData);
        config.sso = sso.prepareToSend();

        // Initialize the Feed SDK
        feedSDK = new FastCommentsFeedSDK(config);

        // Find the feed view in the layout
        feedView = findViewById(R.id.feedView);
        
        // Set the SDK instance for the view
        feedView.setSDK(feedSDK);
        
        feedView.setTagSupplier(currentUser -> {
            // You can customize the user's experience. Only feed items with the same tags will be returned.
            // return null or don't set a supplier to get a "global" feed.
            return null;
        });

        // Create and add the FeedPostCreateView
        setupPostCreationView();

        setupUserClickListener();

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
                Toast.makeText(FeedExampleActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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

            @Override
            public void onCommentsRequested(FeedPost post) {
                // Show comments dialog for the post from the SDK
                CommentsDialog dialog = new CommentsDialog(FeedExampleActivity.this, post, feedSDK);
                
                // Set comment added listener to update the post in the feed
                dialog.setOnCommentAddedListener(postId -> {
                    // Post stats already updated in feedSDK, just need to refresh UI
                    runOnUiThread(() -> {
                        // Find position of post in adapter and update it
                        feedView.refreshPost(postId);
                    });
                });
                
                dialog.setOnUserClickListener(userClickListener);
                
                dialog.show();
            }
        });
        
        feedView.setOnUserClickListener(userClickListener);

        // Load the feed
        feedView.load();
    }

    private void setupUserClickListener() {
        userClickListener = (context, userInfo, source) -> {
            String sourceText = source == UserClickSource.NAME ? "name" : "avatar";
            String contextText = context.isComment() ? "comment" : "feed post";
            Toast.makeText(FeedExampleActivity.this, 
                "Clicked " + userInfo.getDisplayName() + "'s " + sourceText + " in " + contextText, 
                Toast.LENGTH_SHORT).show();
        };
    }

    /**
     * Set up the post creation view and floating action button
     */
    private void setupPostCreationView() {
        // Get the parent ConstraintLayout
        final ConstraintLayout parentLayout = findViewById(R.id.feedParentLayout);

        // Create the post creation view
        postCreateView = new FeedPostCreateView(this);
        postCreateView.setId(View.generateViewId());
        postCreateView.setSDK(feedSDK);
        postCreateView.setVisibility(View.GONE);

        // Create constraints for post creation view (full width at top, floating above content)
        ConstraintLayout.LayoutParams postCreateParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT);
        postCreateView.setLayoutParams(postCreateParams);
        postCreateView.setElevation(16f); // Elevate above other content
        
        // Add view to parent
        parentLayout.addView(postCreateView);

        // Update constraints to position the view at the top
        ConstraintSet initialConstraintSet = new ConstraintSet();
        initialConstraintSet.clone(parentLayout);
        initialConstraintSet.connect(postCreateView.getId(), ConstraintSet.TOP, parentLayout.getId(), ConstraintSet.TOP, 0);
        initialConstraintSet.connect(postCreateView.getId(), ConstraintSet.START, parentLayout.getId(), ConstraintSet.START, 0);
        initialConstraintSet.connect(postCreateView.getId(), ConstraintSet.END, parentLayout.getId(), ConstraintSet.END, 0);
        initialConstraintSet.applyTo(parentLayout);

        // Create FAB
        createPostFab = new FloatingActionButton(this);
        createPostFab.setId(View.generateViewId());
        createPostFab.setImageResource(android.R.drawable.ic_input_add);
        createPostFab.setContentDescription(getString(R.string.create_new_post));

        // Create params for FAB
        ConstraintLayout.LayoutParams fabParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT);
        createPostFab.setLayoutParams(fabParams);

        // Add FAB to parent
        parentLayout.addView(createPostFab);

        // Update constraints to position the FAB at bottom-end
        ConstraintSet fabConstraintSet = new ConstraintSet();
        fabConstraintSet.clone(parentLayout);
        fabConstraintSet.connect(createPostFab.getId(), ConstraintSet.BOTTOM, parentLayout.getId(), ConstraintSet.BOTTOM, 32);
        fabConstraintSet.connect(createPostFab.getId(), ConstraintSet.END, parentLayout.getId(), ConstraintSet.END, 32);
        fabConstraintSet.applyTo(parentLayout);

        // Set FAB click listener
        createPostFab.setOnClickListener(v -> {
            // Configure post creation view and prepare it for showing
            postCreateView.show();
            createPostFab.setVisibility(View.GONE);
            
            // Apply animation to slide it down
            postCreateView.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                    FeedExampleActivity.this, com.fastcomments.sdk.R.anim.slide_down_from_top));
        });

        // Set post creation listener
        postCreateView.setOnPostCreateListener(new FeedPostCreateView.OnPostCreateListener() {
            @Override
            public void onPostCreated(FeedPost post) {
                // Use our new slide up and fade animation
                android.view.animation.Animation slideUpFade = android.view.animation.AnimationUtils.loadAnimation(
                        FeedExampleActivity.this, com.fastcomments.sdk.R.anim.slide_up_and_fade);
                
                slideUpFade.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(android.view.animation.Animation animation) {
                        // No need to change visibility yet
                    }
                    
                    @Override
                    public void onAnimationEnd(android.view.animation.Animation animation) {
                        // Make form completely gone and unclickable
                        postCreateView.clearAnimation();
                        postCreateView.setVisibility(View.GONE);
                        postCreateView.setClickable(false);
                        postCreateView.setEnabled(false);
                        
                        // Show FAB button
                        createPostFab.setVisibility(View.VISIBLE);
                        
                        // Refresh the feed to show the new post
                        feedView.refresh();
                    }
                    
                    @Override
                    public void onAnimationRepeat(android.view.animation.Animation animation) {}
                });
                
                postCreateView.startAnimation(slideUpFade);
                
                // Refresh the feed to show the new post
                feedView.refresh();
            }

            @Override
            public void onPostCreateError(String errorMessage) {
                Toast.makeText(FeedExampleActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPostCreateCancelled() {
                // Use our new slide up and fade animation
                android.view.animation.Animation slideUpFade = android.view.animation.AnimationUtils.loadAnimation(
                        FeedExampleActivity.this, com.fastcomments.sdk.R.anim.slide_up_and_fade);
                
                slideUpFade.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(android.view.animation.Animation animation) {
                        // No need to change visibility yet
                    }
                    
                    @Override
                    public void onAnimationEnd(android.view.animation.Animation animation) {
                        // Make form completely gone and unclickable
                        postCreateView.clearAnimation();
                        postCreateView.setVisibility(View.GONE);
                        postCreateView.setClickable(false);
                        postCreateView.setEnabled(false);
                        
                        // Show FAB button
                        createPostFab.setVisibility(View.VISIBLE);
                    }
                    
                    @Override
                    public void onAnimationRepeat(android.view.animation.Animation animation) {}
                });
                
                postCreateView.startAnimation(slideUpFade);
            }

            @Override
            public void onImagePickerRequested() {
                // Launch image picker
                pickImageLauncher.launch("image/*");
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the feed view to prevent memory leaks
        if (feedView != null) {
            feedView.cleanup();
        }
    }
}