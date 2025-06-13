# FastComments Android SDK - Color Theming Guide

The FastComments Android SDK supports extensive color customization through color resource overrides. This allows you to match the commenting interface with your app's branding.

## Quick Start

### Method 1: Override Color Resources

Override the FastComments color resources in your app's `colors.xml`:

```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <!-- Override FastComments colors with your brand colors -->
    <color name="fastcomments_action_button_color">#FF1976D2</color>  <!-- Blue -->
    <color name="fastcomments_reply_button_color">#FF1976D2</color>
    <color name="fastcomments_toggle_replies_button_color">#FF1976D2</color>
    <color name="fastcomments_load_more_button_text_color">#FF1976D2</color>
    
    <!-- Customize link colors -->
    <color name="fastcomments_link_color">#FF1976D2</color>
    <color name="fastcomments_link_color_pressed">#FF1565C0</color>
    
    <!-- Customize dialog colors -->
    <color name="fastcomments_dialog_header_background">#FF1976D2</color>
    <color name="fastcomments_online_indicator_color">#FF4CAF50</color>
</resources>
```

### Method 2: Override Base Colors

You can also override the base primary colors that many FastComments colors reference:

```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <!-- Change the base primary color (affects multiple components) -->
    <color name="primary">#FF1976D2</color>  <!-- Blue instead of purple -->
    <color name="primary_light">#FF42A5F5</color>
    <color name="primary_dark">#FF1565C0</color>
</resources>
```

### Method 3: Theme Attributes (Advanced)

For advanced theming, you can use the provided theme attributes:

```xml
<!-- In your app's styles.xml -->
<style name="MyApp.FastComments" parent="Theme.FastComments">
    <item name="fastcomments_primary_color">#FF1976D2</item>
    <item name="fastcomments_link_color">#FF1976D2</item>
</style>
```

Then apply this theme to your Activity:

```xml
<!-- In your AndroidManifest.xml -->
<activity
    android:name=".CommentsActivity"
    android:theme="@style/MyApp.FastComments" />
```
```

## Available Color Resources

### Primary Color Resources (Override these to change your brand color)

| Color Resource | Description | Default Value |
|----------------|-------------|---------------|
| `fastcomments_action_button_color` | Color for action buttons (vote, reply, etc.) | Purple (#FF6200EE) |
| `fastcomments_reply_button_color` | Color for reply buttons | Purple (#FF6200EE) |
| `fastcomments_toggle_replies_button_color` | Color for toggle replies buttons | Purple (#FF6200EE) |
| `fastcomments_load_more_button_text_color` | Color for load more button text | Purple (#FF6200EE) |

### Interface Color Resources

| Color Resource | Description | Default Value |
|----------------|-------------|---------------|
| `fastcomments_link_color` | Color for clickable links | Blue (#0099FF) |
| `fastcomments_link_color_pressed` | Color for pressed/focused links | Dark Blue (#0077CC) |
| `fastcomments_dialog_header_background` | Background color for dialog headers | Dark Gray (#333333) |
| `fastcomments_dialog_header_text_color` | Text color for dialog headers | White |
| `fastcomments_online_indicator_color` | Color for online status indicator in live chat | Green (#4CAF50) |
| `fastcomments_vote_count_color` | Color for vote count text | Black |
| `fastcomments_vote_count_zero_color` | Color for zero vote count | Gray (#808080) |
| `fastcomments_vote_divider_color` | Color for vote divider lines | Light Gray (#DDDDDD) |

### Base Color Resources (Affects multiple components)

| Color Resource | Description | Default Value |
|----------------|-------------|---------------|
| `primary` | Base primary color (affects many components) | Purple (#FF6200EE) |
| `primary_light` | Light variant of primary color | Light Purple (#FFBB86FC) |
| `primary_dark` | Dark variant of primary color | Dark Purple (#FF3700B3) |

### Theme Attributes (Advanced)

| Attribute | Description | Default Value |
|-----------|-------------|---------------|
| `fastcomments_primary_color` | Main brand color for buttons, reply actions | Purple (#FF6200EE) |
| `fastcomments_primary_light_color` | Light variant of primary color | Light Purple (#FFBB86FC) |
| `fastcomments_primary_dark_color` | Dark variant of primary color | Dark Purple (#FF3700B3) |
| `fastcomments_secondary_color` | Secondary accent color | Teal (#FF03DAC5) |
| `fastcomments_link_color` | Color for clickable links | Blue (#0099FF) |
| `fastcomments_link_color_pressed` | Color for pressed/focused links | Dark Blue (#0077CC) |
| `fastcomments_action_button_color` | Color for action buttons (vote, reply, etc.) | Primary Color |
| `fastcomments_dialog_header_background` | Background color for dialog headers | Dark Gray (#333333) |
| `fastcomments_dialog_header_text_color` | Text color for dialog headers | White |
| `fastcomments_online_indicator_color` | Color for online status indicator in live chat | Green (#4CAF50) |

## Example Themes

### Red Theme (Method 1: Color Resources)
```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <color name="fastcomments_action_button_color">#FFD32F2F</color>
    <color name="fastcomments_reply_button_color">#FFD32F2F</color>
    <color name="fastcomments_toggle_replies_button_color">#FFD32F2F</color>
    <color name="fastcomments_load_more_button_text_color">#FFD32F2F</color>
    <color name="fastcomments_link_color">#FFD32F2F</color>
    <color name="fastcomments_link_color_pressed">#FFC62828</color>
    
    <!-- Or override the base primary color -->
    <color name="primary">#FFD32F2F</color>
    <color name="primary_light">#FFEF5350</color>
    <color name="primary_dark">#FFC62828</color>
</resources>
```

### Green Theme (Method 1: Color Resources)
```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <color name="fastcomments_action_button_color">#FF388E3C</color>
    <color name="fastcomments_reply_button_color">#FF388E3C</color>
    <color name="fastcomments_toggle_replies_button_color">#FF388E3C</color>
    <color name="fastcomments_load_more_button_text_color">#FF388E3C</color>
    <color name="fastcomments_link_color">#FF388E3C</color>
    <color name="fastcomments_link_color_pressed">#FF2E7D32</color>
    
    <!-- Or override the base primary color -->
    <color name="primary">#FF388E3C</color>
    <color name="primary_light">#FF66BB6A</color>
    <color name="primary_dark">#FF2E7D32</color>
</resources>
```

### Blue Theme (Method 2: Base Color Override)
```xml
<!-- In your app's res/values/colors.xml -->
<resources>
    <!-- This approach changes the base color, affecting all primary color references -->
    <color name="primary">#FF1976D2</color>
    <color name="primary_light">#FF42A5F5</color>
    <color name="primary_dark">#FF1565C0</color>
    
    <!-- Optionally customize other colors -->
    <color name="fastcomments_link_color">#FF1976D2</color>
    <color name="fastcomments_link_color_pressed">#FF1565C0</color>
</resources>
```

### Advanced Theme (Method 3: Theme Attributes)
```xml
<!-- In your app's styles.xml -->
<style name="MyApp.FastComments.Custom" parent="Theme.FastComments">
    <item name="fastcomments_primary_color">#FFFF9800</item>  <!-- Orange accent -->
    <item name="fastcomments_primary_light_color">#FFFFB74D</item>
    <item name="fastcomments_primary_dark_color">#FFF57C00</item>
    <item name="fastcomments_dialog_header_background">#FF212121</item>
    <item name="fastcomments_dialog_header_text_color">#FFFFFFFF</item>
    <item name="fastcomments_action_button_color">#FFFF9800</item>
    <item name="fastcomments_link_color">#FFFF9800</item>
    <item name="fastcomments_link_color_pressed">#FFF57C00</item>
</style>
```

## Components Affected

The following FastComments components will use your custom colors:

- **Reply buttons** - Uses `fastcomments_primary_color`
- **Vote buttons** - Uses `fastcomments_action_button_color`  
- **Toggle replies buttons** - Uses `fastcomments_primary_color`
- **Load more buttons** - Uses `fastcomments_primary_color`
- **Clickable links in comments** - Uses `fastcomments_link_color`
- **Dialog headers** - Uses `fastcomments_dialog_header_background` and `fastcomments_dialog_header_text_color`
- **Live chat online indicator** - Uses `fastcomments_online_indicator_color`

## Migration from Previous Versions

If you were previously using the SDK with the default purple theme, no changes are required. The new theming system is fully backward compatible.

If you need to maintain the exact same purple colors, you can explicitly set them:

```xml
<style name="MyApp.FastComments.Purple" parent="Theme.FastComments">
    <!-- These are the same as the defaults, but explicitly set -->
    <item name="fastcomments_primary_color">#FF6200EE</item>
    <item name="fastcomments_primary_light_color">#FFBB86FC</item>
    <item name="fastcomments_primary_dark_color">#FF3700B3</item>
</style>
```

## Night/Dark Mode Support

The theming system works with Android's night mode. You can create separate theme files for dark mode:

```xml
<!-- res/values-night/themes.xml -->
<style name="MyApp.FastComments" parent="Theme.FastComments">
    <!-- Dark mode specific colors -->
    <item name="fastcomments_primary_color">#FFBB86FC</item>  <!-- Lighter primary for dark mode -->
    <item name="fastcomments_dialog_header_background">#FF1E1E1E</item>
    <item name="fastcomments_dialog_header_text_color">#FFFFFFFF</item>
</style>
```

## Troubleshooting

### Colors Not Applying
1. Ensure you're applying the theme to the correct Activity
2. Make sure to call `setTheme()` before `super.onCreate()` if setting programmatically
3. Verify your color values are in the correct format (`#AARRGGBB` or `#RRGGBB`)

### Partial Color Changes
Some colors may not change immediately if they're cached. Try:
1. Clean and rebuild your project
2. Restart the app completely
3. Check that you're overriding the correct attribute name

For additional support, please refer to the FastComments documentation or contact support.