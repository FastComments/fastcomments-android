package com.fastcomments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView

class MainActivity : AppCompatActivity() {
    
    private lateinit var commentsView: FastCommentsView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val config = CommentWidgetConfig(
            "demo",
            "android-demo-page",
            "https://example.com/some-page",
            "example.com",
            "Some Page Title"
        )

        // TODO simple sso example
        // TODO secure sso example
        // TODO all same examples as React library

        val sdk = FastCommentsSDK(config)

        val container = findViewById<android.widget.FrameLayout>(R.id.commentsContainer)
        commentsView = FastCommentsView(this, sdk)
        container.addView(commentsView)
        commentsView.load()
    }
    
    companion object {
        private const val TAG = "FastCommentsExample"
    }
}