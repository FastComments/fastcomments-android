package com.fastcomments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.sso.FastCommentsSSO
import com.fastcomments.core.sso.SimpleSSOUserData
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView

class SimpleSSOExampleActivity : AppCompatActivity() {

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

        val userData = SimpleSSOUserData("Example User", "user@example.com", "https://staticm.fastcomments.com/1639362726066-DSC_0841.JPG");
        val sso = FastCommentsSSO(userData)
        config.sso = sso.prepareToSend()
        val sdk = FastCommentsSDK(config)

        // Find the comments view in the layout
        commentsView = findViewById(R.id.commentsView)

        // Set the SDK instance for the view
        commentsView.setSDK(sdk)
        commentsView.load()
    }

    companion object {
        private const val TAG = "FastCommentsExample"
    }
}