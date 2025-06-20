package com.fastcomments

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.VoteStyle
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsTheme
import com.fastcomments.sdk.FastCommentsView
import com.fastcomments.sdk.OnUserClickListener
import com.fastcomments.sdk.UserClickSource
import com.fastcomments.sdk.UserInfo
import com.fastcomments.sdk.UserClickContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var commentsView: FastCommentsView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configure the SDK
        val config = CommentWidgetConfig(
            "demo", // Use your tenant ID
            "https://example.com/page1", // Use your URL ID
            "Example Page", // Page title
            "example.com", // Domain
            "Example Demo" // Site name
        )
        
        // Optional configuration
        // config.voteStyle = VoteStyle.Heart
        // config.enableInfiniteScrolling = true
        
        // Initialize the SDK
        val sdk = FastCommentsSDK(config)
        
        // Optional: Set a custom theme programmatically
        val resources = resources
        val theme = FastCommentsTheme.Builder()
            .setPrimaryColor(resources.getColor(com.fastcomments.sdk.R.color.primary, null))
            .setPrimaryLightColor(resources.getColor(com.fastcomments.sdk.R.color.primary_light, null))
            .setPrimaryDarkColor(resources.getColor(com.fastcomments.sdk.R.color.primary_dark, null))
            
            // Button theming
            .setActionButtonColor(android.graphics.Color.parseColor("#FF1976D2"))
            .setReplyButtonColor(android.graphics.Color.parseColor("#FF4CAF50"))
            .setToggleRepliesButtonColor(android.graphics.Color.parseColor("#FFFF5722"))
            .setLoadMoreButtonTextColor(android.graphics.Color.parseColor("#FF9C27B0"))
            
            // Other UI colors
            .setLinkColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_link_color, null))
            .setLinkColorPressed(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_link_color_pressed, null))
            .setVoteCountColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_vote_count_color, null))
            .setVoteCountZeroColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_vote_count_zero_color, null))
            .setVoteDividerColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_vote_divider_color, null))
            .setDialogHeaderBackgroundColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_dialog_header_background, null))
            .setDialogHeaderTextColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_dialog_header_text_color, null))
            .setOnlineIndicatorColor(resources.getColor(com.fastcomments.sdk.R.color.fastcomments_online_indicator_color, null))
            .build()
        
        // Apply the theme to the SDK
        sdk.setTheme(theme)

        // Find the comments view in the layout
        commentsView = findViewById(R.id.commentsView)
        
        // Set the SDK instance for the view
        commentsView.setSDK(sdk)
        
        commentsView.setOnUserClickListener { context, userInfo, source ->
            val sourceText = if (source == UserClickSource.NAME) "name" else "avatar"
            Toast.makeText(this@MainActivity, "Clicked ${userInfo.displayName}'s $sourceText", Toast.LENGTH_SHORT).show()
        }
        
        // Load comments
        commentsView.load()
    }
    
    companion object {
        private const val TAG = "FastCommentsExample"
    }
}