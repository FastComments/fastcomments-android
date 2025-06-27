package com.fastcomments

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.sso.FastCommentsSSO
import com.fastcomments.core.sso.SimpleSSOUserData
import com.fastcomments.sdk.LiveChatView
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.OnUserClickListener
import com.fastcomments.sdk.UserClickSource
import com.fastcomments.sdk.UserInfo
import com.fastcomments.sdk.UserClickContext

/**
 * Example activity showing how to use the LiveChatView
 */
class LiveChatExampleActivity : AppCompatActivity() {

    private lateinit var liveChatView: LiveChatView
    private lateinit var sdk: FastCommentsSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example_live_chat)

        // Create a configuration for the SDK
        val config = CommentWidgetConfig().apply {
            tenantId = "demo" // Use your tenant ID here
            urlId = "https://example.com/chat-room" // Use your URL ID or chat room identifier
            // Optional: Set additional configuration options
            pageTitle = "Example Chat Room"
        }
        LiveChatView.setupLiveChatConfig(config)

        // Initialize the SDK. The below uses SimpleSSO for the demo. You probably want to use SecureSSO with a backend service.
        val userData = SimpleSSOUserData(
            "Example User",
            "user@example.com",
            "https://staticm.fastcomments.com/1639362726066-DSC_0841.JPG"
        );
        val sso = FastCommentsSSO(userData)
        config.sso = sso.prepareToSend()
        sdk = FastCommentsSDK(config)

        // Find the live chat view in the layout
        liveChatView = findViewById(R.id.liveChatView)

        // Set the SDK instance for the view
        liveChatView.setSDK(sdk)
        
        liveChatView.setOnUserClickListener { context, userInfo, source ->
            val sourceText = if (source == UserClickSource.NAME) "name" else "avatar"
            Toast.makeText(this@LiveChatExampleActivity, 
                "Clicked ${userInfo.displayName}'s $sourceText in live chat", 
                Toast.LENGTH_SHORT).show()
        }

        // Load the chat
        liveChatView.load()
    }

    override fun onResume() {
        super.onResume()
        sdk.refreshLiveEvents()
        liveChatView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        liveChatView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        liveChatView.cleanup()
        sdk.cleanup()
    }
}