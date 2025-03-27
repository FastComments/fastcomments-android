package com.fastcomments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.VoteStyle
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView

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

        // Find the comments view in the layout
        commentsView = findViewById(R.id.commentsView)
        
        // Set the SDK instance for the view
        commentsView.setSDK(sdk)
        
        // Load comments
        commentsView.load()
    }
    
    companion object {
        private const val TAG = "FastCommentsExample"
    }
}