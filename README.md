# FastComments Android SDK

[![Repsy](https://img.shields.io/badge/Repsy-Repository-blue)](https://repo.repsy.io/mvn/winrid/fastcomments)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

The FastComments Android SDK provides a simple way to add real-time commenting functionality to your Android applications.

## Features

- Live commenting with real-time updates
- Native Android UI components
- Threaded discussions with replies
- Secure SSO authentication
- Voting system with customizable styles
- User notifications and presence
- Comment moderation capabilities
- Social feed integration
- Infinite scroll pagination
- Comprehensive theming

## Requirements

- Android SDK 26+ (Android 8.0 Oreo or later)
- Java 8+

## Installation

Add the FastComments SDK to your app's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.fastcomments:sdk:0.0.1")
}
```

Make sure you have the Repsy repository in your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://repo.repsy.io/mvn/winrid/fastcomments")
        }
        // other repositories...
    }
}
```

## Basic Usage

### 1. Add FastCommentsView to your layout

```xml
<com.fastcomments.sdk.FastCommentsView
    android:id="@+id/commentsView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 2. Initialize and configure the SDK

```kotlin
// Configure the SDK
val config = CommentWidgetConfig(
    "your-tenant-id", 
    "page-url-id", 
    "Page Title", 
    "yourdomain.com", 
    "Site Name"
)

// Additional configuration options
config.voteStyle = VoteStyle.UpDown // or VoteStyle.Heart
config.enableInfiniteScrolling = true
config.hasDarkBackground = true // for dark mode support

// Initialize the SDK
val sdk = FastCommentsSDK(config)

// Find the comments view in your layout
val commentsView = findViewById<FastCommentsView>(R.id.commentsView)

// Set the SDK instance for the view
commentsView.setSDK(sdk)

// Load comments
commentsView.load()
```

## Secure SSO Authentication

Implement secure authentication for your users:

```kotlin
// Create user data (ideally on your server)
val userData = SecureSSOUserData(
    "user-id",
    "user@example.com",
    "User Name",
    "https://path-to-avatar.jpg"
)

// Generate SSO token (should be done server-side!)
val sso = FastCommentsSSO.createSecure("YOUR_API_KEY", userData)
val token = sso.prepareToSend()

// Add to config
config.sso = token
```

## Feed Integration

Display a social media style feed with comments:

```java
// Configure the SDK
CommentWidgetConfig config = new CommentWidgetConfig();
config.tenantId = "your-tenant-id";
config.urlId = "page-url-id";

// Initialize the Feed SDK
FastCommentsFeedSDK feedSDK = new FastCommentsFeedSDK(config);

// Set up the feed view
FastCommentsFeedView feedView = findViewById(R.id.feedView);
feedView.setSDK(feedSDK);

// Set interaction listener
feedView.setFeedViewInteractionListener(new FastCommentsFeedView.OnFeedViewInteractionListener() {
    @Override
    public void onFeedLoaded(List<FeedPost> posts) {
        // Feed loaded successfully
    }

    @Override
    public void onFeedError(String errorMessage) {
        // Handle errors
    }

    @Override
    public void onPostSelected(FeedPost post) {
        // User selected a post
    }

    @Override
    public void onCommentsRequested(FeedPost post) {
        // Show comments for the post
        CommentsDialog dialog = new CommentsDialog(context, post, feedSDK);
        dialog.show();
    }
});

// Load the feed
feedView.load();
```

## Live Chat Integration

Add a real-time chat interface to your app:

```kotlin
// Add LiveChatView to your layout XML
// <com.fastcomments.sdk.LiveChatView
//     android:id="@+id/liveChatView"
//     android:layout_width="match_parent"
//     android:layout_height="match_parent" />

// Create a configuration for the SDK
val config = CommentWidgetConfig().apply {
    tenantId = "your-tenant-id"
    urlId = "chat-room-identifier" 
    pageTitle = "Chat Room Name"
}
LiveChatView.setupLiveChatConfig(config)

// Optional: Add user authentication
val userData = SimpleSSOUserData(
    "User Name",
    "user@example.com",
    "https://path-to-avatar.jpg"
)
val sso = FastCommentsSSO(userData)
config.sso = sso.prepareToSend()

// Initialize the SDK
val sdk = FastCommentsSDK().configure(config)

// Set up the live chat view
val liveChatView = findViewById<LiveChatView>(R.id.liveChatView)
liveChatView.setSDK(sdk)
liveChatView.load()

// Don't forget lifecycle handling
override fun onResume() {
    super.onResume()
    sdk.refreshLiveEvents()
}

override fun onDestroy() {
    super.onDestroy()
    sdk.cleanup()
}
```

## Example Projects

Check out these demo implementations:

- [Basic Comments Example](app/src/main/java/com/fastcomments/MainActivity.kt)
- [Secure SSO Implementation](app/src/main/java/com/fastcomments/SecureSSOExampleActivity.kt)
- [Feed Integration Example](app/src/main/java/com/fastcomments/FeedExampleActivity.java)
- [Live Chat Example](app/src/main/java/com/fastcomments/LiveChatExampleActivity.kt)
- [Comments Dialog Example](app/src/main/java/com/fastcomments/FeedExampleActivity.java)

## Configuration Options

The SDK provides many configuration options through the `CommentWidgetConfig` class:

| Option | Description |
|--------|-------------|
| `tenantId` | Your FastComments account ID |
| `urlId` | ID representing the current page |
| `sso` | SSO token for authentication |
| `allowAnon` | Allow anonymous commenting |
| `voteStyle` | UpDown or Heart voting style |
| `hideAvatars` | Hide user avatars |
| `hasDarkBackground` | Indicates dark mode |
| `customCSS` | Custom CSS styles |
| `enableInfiniteScrolling` | Enable infinite scroll pagination |
| `readonly` | Disable commenting but show comments |
| `disableVoting` | Disable voting functionality |
| `disableLiveCommenting` | Disable real-time updates |

## Comprehensive Theme Customization

All buttons and UI elements in the FastComments SDK are themeable. Use the `FastCommentsTheme.Builder` for complete control over your app's branding.

### Programmatic Theming (Recommended)

```kotlin
val theme = FastCommentsTheme.Builder()
    // Action buttons: Send, vote, menu, like/share buttons
    .setActionButtonColor(Color.parseColor("#FF1976D2"))
    
    // Reply buttons: Comment reply buttons  
    .setReplyButtonColor(Color.parseColor("#FF4CAF50"))
    
    // Toggle buttons: Show/hide replies buttons
    .setToggleRepliesButtonColor(Color.parseColor("#FFFF5722"))
    
    // Load more buttons: Pagination buttons
    .setLoadMoreButtonTextColor(Color.parseColor("#FF9C27B0"))
    
    .setPrimaryColor(Color.parseColor("#FF6200EE"))
    .setLinkColor(Color.parseColor("#FF1976D2"))
    .setDialogHeaderBackgroundColor(Color.parseColor("#FF333333"))
    .build()

// Apply the theme
sdk.setTheme(theme)
```

### Quick Color Override

Override color resources in your `colors.xml` for simple branding:

```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <!-- Change all primary UI elements -->
    <color name="primary">#FF1976D2</color>
    
    <!-- Or customize specific button types -->
    <color name="fastcomments_action_button_color">#FF1976D2</color>
    <color name="fastcomments_reply_button_color">#FF4CAF50</color>
    <color name="fastcomments_toggle_replies_button_color">#FFFF5722</color>
    <color name="fastcomments_load_more_button_text_color">#FF9C27B0</color>
</resources>
```

### Themed Button Coverage

**Every button in the SDK supports theming:**
- Send buttons, vote buttons, menu buttons, reply buttons
- Show/hide replies buttons, load more buttons  
- Feed action buttons (like, comment, share)
- Dialog buttons (submit, cancel, save)
- Dynamic task buttons in feed posts

For detailed theming documentation, see [THEMING.md](THEMING.md).

## Memory Management

### Preventing Memory Leaks

To prevent memory leaks when using FastComments views in Activities or Fragments, always call `cleanup()` when the view is no longer needed:

#### In Activities:
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    // Clean up FastComments views to prevent memory leaks
    if (feedView != null) {
        feedView.cleanup();
    }
    if (commentsView != null) {
        commentsView.cleanup();
    }
}
```

#### In Fragments:
```java
@Override
public void onDestroyView() {
    super.onDestroyView();
    // Clean up FastComments views when fragment view is destroyed
    if (feedView != null) {
        feedView.cleanup();
        feedView = null;
    }
}

@Override
public void onDestroy() {
    super.onDestroy();
    // Additional cleanup when fragment is destroyed
    if (feedSDK != null) {
        feedSDK.cleanup();
        feedSDK = null;
    }
}
```

#### When Switching Fragments:
```java
// Before replacing or removing a fragment containing FastComments views
Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.container);
if (currentFragment instanceof YourFragmentWithFeedView) {
    ((YourFragmentWithFeedView) currentFragment).cleanupFeedView();
}

// Then proceed with fragment transaction
getSupportFragmentManager().beginTransaction()
    .replace(R.id.container, newFragment)
    .commit();
```

**Important**: Always call `cleanup()` methods to prevent memory leaks, especially when:
- Activities are destroyed
- Fragment views are destroyed
- Switching between fragments
- Navigating away from screens with FastComments components

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For questions or issues, please contact [support@fastcomments.com](mailto:support@fastcomments.com) or visit [fastcomments.com/auth/my-account/help](https://fastcomments.com/auth/my-account/help).