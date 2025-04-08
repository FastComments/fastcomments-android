package com.fastcomments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.VoteStyle
import com.fastcomments.core.sso.FastCommentsSSO
import com.fastcomments.core.sso.SecureSSOUserData
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView

class SecureSSOExampleActivity : AppCompatActivity() {

    private lateinit var commentsView: FastCommentsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val config = CommentWidgetConfig(
            "demo",
            "ssotest",
            "https://fastcomments.com/demo",
            "fastcomments.com",
            "Demo"
        )
        config.sso = getSSOTokenFromServer();
        val sdk = FastCommentsSDK(config)

        // Find the comments view in the layout
        commentsView = findViewById(R.id.commentsView)
        
        // Set the SDK instance for the view
        commentsView.setSDK(sdk)
        commentsView.load()
    }

    private fun getSSOTokenFromServer(): String {
        // DO THIS ON THE SERVER. THIS IS ONLY IN THE APP AS AN EXAMPLE!
        val userData = SecureSSOUserData("user-123", "user@example.com", "Example User", "https://staticm.fastcomments.com/1639362726066-DSC_0841.JPG");
        userData.displayName = "Fancy Name";
        val sso = FastCommentsSSO.createSecure("YOUR_API_KEY", userData);
        return sso.prepareToSend(); // send to client
    }

    companion object {
        private const val TAG = "FastCommentsExample"
    }
}
