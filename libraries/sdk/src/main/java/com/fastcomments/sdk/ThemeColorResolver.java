package com.fastcomments.sdk;

import android.content.Context;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Helper class to resolve colors from theme or fallback to resources
 */
public class ThemeColorResolver {
    
    /**
     * Get color from theme or fallback to resource
     * 
     * @param context The context to use for resource lookup
     * @param theme The theme to check first (can be null)
     * @param colorGetter Function to get color from theme
     * @param fallbackResId The resource ID to use if theme doesn't have the color
     * @return The resolved color
     */
    @ColorInt
    public static int getColor(@NonNull Context context, 
                              @Nullable FastCommentsTheme theme,
                              @NonNull ColorGetter colorGetter,
                              @ColorRes int fallbackResId) {
        if (theme != null) {
            Integer themeColor = colorGetter.getColor(theme);
            if (themeColor != null) {
                return themeColor;
            }
        }
        return ContextCompat.getColor(context, fallbackResId);
    }
    
    /**
     * Functional interface for getting color from theme
     */
    public interface ColorGetter {
        @Nullable
        Integer getColor(@NonNull FastCommentsTheme theme);
    }
    
    // Convenience methods for specific colors
    
    @ColorInt
    public static int getVoteCountColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getVoteCountColor, R.color.fastcomments_vote_count_color);
    }
    
    @ColorInt
    public static int getVoteCountZeroColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getVoteCountZeroColor, R.color.fastcomments_vote_count_zero_color);
    }
    
    @ColorInt
    public static int getReplyButtonColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getReplyButtonColor, R.color.fastcomments_reply_button_color);
    }
    
    @ColorInt
    public static int getToggleRepliesButtonColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getToggleRepliesButtonColor, R.color.fastcomments_toggle_replies_button_color);
    }
    
    @ColorInt
    public static int getActionButtonColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getActionButtonColor, R.color.fastcomments_action_button_color);
    }
    
    @ColorInt
    public static int getLoadMoreButtonTextColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getLoadMoreButtonTextColor, R.color.fastcomments_load_more_button_text_color);
    }
    
    @ColorInt
    public static int getLinkColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getLinkColor, R.color.fastcomments_link_color);
    }
    
    @ColorInt
    public static int getLinkColorPressed(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getLinkColorPressed, R.color.fastcomments_link_color_pressed);
    }
    
    @ColorInt
    public static int getOnlineIndicatorColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getOnlineIndicatorColor, R.color.fastcomments_online_indicator_color);
    }
    
    @ColorInt
    public static int getDialogHeaderBackgroundColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getDialogHeaderBackgroundColor, R.color.fastcomments_dialog_header_background);
    }
    
    @ColorInt
    public static int getDialogHeaderTextColor(@NonNull Context context, @Nullable FastCommentsTheme theme) {
        return getColor(context, theme, FastCommentsTheme::getDialogHeaderTextColor, R.color.fastcomments_dialog_header_text_color);
    }
}