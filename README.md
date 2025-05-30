# FastComments Android SDK

[![Repsy](https://img.shields.io/badge/Repsy-Repository-blue)](https://repo.repsy.io/mvn/winrid/fastcomments)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

The FastComments Android SDK provides a simple way to add real-time commenting functionality to your Android applications.

## Features

- 🔄 Live commenting with real-time updates
- 📱 Native Android UI components
- 🧵 Threaded discussions with replies
- 👤 Secure SSO authentication
- 👍 Voting system with customizable styles
- 🔔 User notifications and presence
- 🔍 Comment moderation capabilities
- 📱 Social feed integration
- ♾️ Infinite scroll pagination

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

## Customizing Colors

You can customize the appearance of the FastComments components by overriding the color resources in your app's `colors.xml` file:

```xml
<!-- Custom FastComments colors -->
<color name="fastcomments_dialog_header_background">#FF4CAF50</color>
<color name="fastcomments_dialog_header_text_color">#FFFFFFFF</color>
```

Available color customization options:

| Color Resource | Default | Description |
|----------------|---------|-------------|
| `fastcomments_dialog_header_background` | Blue | Background color of the comments dialog header |
| `fastcomments_dialog_header_text_color` | White | Text color of the comments dialog title |
| `fastcomments_reply_button_color` | Primary | Color of the reply button |
| `fastcomments_toggle_replies_button_color` | Primary | Color of the toggle replies button |
| `fastcomments_link_color` | #0099FF | Color of links in comments |
| `fastcomments_link_color_pressed` | #0077CC | Color of links when pressed |
| `fastcomments_action_button_color` | Primary | Color of action buttons |
| `fastcomments_load_more_button_text_color` | Primary | Color of load more button text |

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