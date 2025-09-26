package com.fastcomments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fastcomments.core.CommentWidgetConfig
import com.fastcomments.core.VoteStyle
import com.fastcomments.core.sso.FastCommentsSSO
import com.fastcomments.core.sso.SimpleSSOUserData
import com.fastcomments.sdk.FastCommentsSDK
import com.fastcomments.sdk.FastCommentsView
import com.fastcomments.sdk.examples.CodeFormattingToolbarButton
import com.fastcomments.sdk.examples.EmojiPickerToolbarButton
import com.fastcomments.sdk.examples.GifPickerToolbarButton
import com.fastcomments.sdk.examples.MentionToolbarButton

/**
 * Activity that showcases the FastComments toolbar functionality for prospective developers.
 *
 * This example demonstrates:
 * - How to enable the comment input toolbar
 * - How to configure default formatting buttons (bold, italic, link, code)
 * - How to add custom toolbar buttons (emoji picker, GIF picker, mention, custom code formatter)
 * - Both global SDK configuration and per-instance customization
 * - Best practices for toolbar implementation
 */
class ToolbarShowcaseActivity : AppCompatActivity() {

    private lateinit var commentsView: FastCommentsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Basic FastComments configuration (same as SimpleSSOExampleActivity)
        val config = CommentWidgetConfig(
            "demo",
            "toolbar-showcase", // Different URL slug to avoid comment conflicts
            "https://fastcomments.com/toolbar-demo",
            "fastcomments.com",
            "Toolbar Demo"
        )

        // Optional: Configure vote style
        config.voteStyle = VoteStyle.Heart

        // 2. Set up SSO with example user data
        val userData = SimpleSSOUserData(
            "Toolbar Demo User",
            "toolbar-demo@example.com",
            "https://staticm.fastcomments.com/1639362726066-DSC_0841.JPG"
        )
        val sso = FastCommentsSSO(userData)
        config.sso = sso.prepareToSend()

        // 3. Create SDK instance
        val sdk = FastCommentsSDK(config)

        // 4. TOOLBAR CONFIGURATION - This is the main showcase feature
        configureFastCommentsToolbar(sdk)

        // 5. Set up the comments view
        commentsView = findViewById(R.id.commentsView)
        commentsView.setSDK(sdk)
        commentsView.load()

        // 6. Optional: Demonstrate per-instance toolbar customization
        demonstrateInstanceCustomization()
    }

    /**
     * Configures the FastComments toolbar with various buttons and features.
     * This demonstrates the main toolbar functionality that developers will use.
     */
    private fun configureFastCommentsToolbar(sdk: FastCommentsSDK) {
        // Enable the comment input toolbar globally for all comment inputs
        sdk.setCommentToolbarEnabled(true)

        // Enable default formatting buttons (bold, italic, link)
        // These provide basic text formatting functionality out of the box
        sdk.setDefaultFormattingButtonsEnabled(true)

        // Add custom toolbar buttons that will appear on all comment inputs
        // These demonstrate how to extend the toolbar with custom functionality

        // 1. Emoji Picker - Provides a grid of common emojis
        sdk.addGlobalCustomToolbarButton(EmojiPickerToolbarButton())

        // 2. GIF Picker - Demonstrates file/media selection (simulated for demo)
        sdk.addGlobalCustomToolbarButton(GifPickerToolbarButton())

        // 3. Mention Button - Shows how to implement @mention functionality
        sdk.addGlobalCustomToolbarButton(MentionToolbarButton())

        // 4. Code Formatter - Custom button that we created for this showcase
        // Demonstrates wrapping text in code tags with both click and long-click handlers
        sdk.addGlobalCustomToolbarButton(CodeFormattingToolbarButton())

        // Note: You can also remove buttons dynamically:
        // sdk.removeGlobalCustomToolbarButton("button_id")
        // sdk.clearGlobalCustomToolbarButtons()
    }

    /**
     * Demonstrates additional per-instance customization options.
     * This shows how individual comment input instances can have different toolbar configurations.
     */
    private fun demonstrateInstanceCustomization() {
        // You can customize toolbar settings for specific comment input instances
        // after the SDK has been set. This is useful for different contexts within your app.

        // Example of per-instance customization (commented out for this demo):
        /*
        // Get access to the bottom comment input view
        val bottomInputView = commentsView.getBottomCommentInputView()

        // Override global settings for this specific instance
        bottomInputView?.let { inputView ->
            // Add an instance-specific button
            inputView.addCustomToolbarButton(SomeCustomButton())

            // Or disable default formatting for this instance only
            inputView.setDefaultFormattingEnabled(false)

            // Or hide the toolbar entirely for this instance
            inputView.setToolbarVisible(false)
        }
        */
    }

    companion object {
        private const val TAG = "ToolbarShowcase"
    }
}