package com.fastcomments

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.model.APICommentPublicComment
import com.fastcomments.model.UserSessionInfo
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView

class MainActivity : AppCompatActivity() {
    
    private lateinit var commentsView: FastCommentsView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize the SDK
        val sdk = FastCommentsSDK.init(applicationContext)
        
        // Create a configuration
        val config = createConfig()
        
        // Configure with user information (optional)
        setupUserSession(sdk)
        
        // Create and add the FastCommentsView
        setupCommentsView(config)
    }
    
    private fun createConfig(): CommentWidgetConfig {
        // Create a configuration for the FastComments widget
        // Replace "demo" with your tenant ID in a real application
        val config = FastCommentsSDK.createConfig("demo", "example-android-page")
        
        // Optional: Set additional configuration options
        config.url = "https://example.com/my-article"
        config.pageTitle = "Android SDK Example"
        
        return config
    }
    
    private fun setupUserSession(sdk: FastCommentsSDK) {
        // Example of setting up a user session
        // In a real app, you would get this information from your auth system
        
        // For anonymous commenting:
        // - Don't set a user at all, or
        // - Set only display information
        
        val userInfo = UserSessionInfo().apply {
            // For SSO (authenticated users)
            // userId = "user-123" 
            // userEmail = "user@example.com"
            
            // For anonymous commenting with user info
            userName = "Android User"
            avatarSrc = "https://secure.gravatar.com/avatar/?d=mp&f=y"
        }
        
        // Set the current user in the SDK
        sdk.setCurrentUser(userInfo)
        
        // Example of SSO implementation (commented out)
        /*
        val sso = SSO().apply {
            userID = "user-123"
            username = "Android User" 
            email = "user@example.com"
            
            // SSO authentication requires a secret key and hash
            // You should generate this server-side
            apiKey = "your-api-key"
            // loginHash should be generated on your server:
            // loginHash = hash_hmac('sha256', email + userID, apiKey)
            loginHash = "generated-login-hash"
        }
        
        val configWithSSO = FastCommentsSDK.createConfigWithSSO("your-tenant-id", "page-id", sso)
        */
    }
    
    private fun setupCommentsView(config: CommentWidgetConfig) {
        // Get the container view
        val container = findViewById<android.widget.FrameLayout>(R.id.commentsContainer)
        
        // Create the FastCommentsView
        commentsView = FastCommentsView(this)
        container.addView(commentsView)
        
        // Set configuration
        commentsView.setConfig(config)
        
        // Set up a listener for when comments are loaded
        commentsView.setOnCommentsLoadedListener(object : FastCommentsView.OnCommentsLoadedListener {
            override fun onCommentsLoaded(comments: MutableList<APICommentPublicComment>) {
                Log.d(TAG, "Loaded ${comments.size} comments")
                Toast.makeText(
                    this@MainActivity, 
                    "Loaded ${comments.size} comments", 
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Error loading comments", e)
                Toast.makeText(
                    this@MainActivity, 
                    "Error loading comments: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        
        // Handle comment replies
        commentsView.setOnCommentReplyListener { parentComment ->
            // You could show a focused reply UI or scroll to the comment form
            Toast.makeText(
                this@MainActivity, 
                "Replying to: ${parentComment.author?.name ?: "Anonymous"}", 
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    companion object {
        private const val TAG = "FastCommentsExample"
    }
}