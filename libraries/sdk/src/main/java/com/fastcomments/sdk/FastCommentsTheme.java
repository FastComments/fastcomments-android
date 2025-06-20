package com.fastcomments.sdk;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Theme configuration for FastComments SDK
 * Allows programmatic color customization without requiring XML resource overrides
 */
public class FastCommentsTheme {
    
    // Primary colors
    @Nullable private Integer primaryColor;
    @Nullable private Integer primaryLightColor;
    @Nullable private Integer primaryDarkColor;
    
    // Action colors
    @Nullable private Integer actionButtonColor;
    @Nullable private Integer replyButtonColor;
    @Nullable private Integer toggleRepliesButtonColor;
    @Nullable private Integer loadMoreButtonTextColor;
    
    // Link colors
    @Nullable private Integer linkColor;
    @Nullable private Integer linkColorPressed;
    
    // Vote colors
    @Nullable private Integer voteCountColor;
    @Nullable private Integer voteCountZeroColor;
    @Nullable private Integer voteDividerColor;
    
    // Dialog colors
    @Nullable private Integer dialogHeaderBackgroundColor;
    @Nullable private Integer dialogHeaderTextColor;
    
    // Other UI colors
    @Nullable private Integer onlineIndicatorColor;
    
    /**
     * Builder pattern for easy theme construction
     */
    public static class Builder {
        private final FastCommentsTheme theme = new FastCommentsTheme();
        
        public Builder setPrimaryColor(@ColorInt int color) {
            theme.primaryColor = color;
            return this;
        }
        
        public Builder setPrimaryLightColor(@ColorInt int color) {
            theme.primaryLightColor = color;
            return this;
        }
        
        public Builder setPrimaryDarkColor(@ColorInt int color) {
            theme.primaryDarkColor = color;
            return this;
        }
        
        public Builder setActionButtonColor(@ColorInt int color) {
            theme.actionButtonColor = color;
            return this;
        }
        
        public Builder setReplyButtonColor(@ColorInt int color) {
            theme.replyButtonColor = color;
            return this;
        }
        
        public Builder setToggleRepliesButtonColor(@ColorInt int color) {
            theme.toggleRepliesButtonColor = color;
            return this;
        }
        
        public Builder setLoadMoreButtonTextColor(@ColorInt int color) {
            theme.loadMoreButtonTextColor = color;
            return this;
        }
        
        public Builder setLinkColor(@ColorInt int color) {
            theme.linkColor = color;
            return this;
        }
        
        public Builder setLinkColorPressed(@ColorInt int color) {
            theme.linkColorPressed = color;
            return this;
        }
        
        public Builder setVoteCountColor(@ColorInt int color) {
            theme.voteCountColor = color;
            return this;
        }
        
        public Builder setVoteCountZeroColor(@ColorInt int color) {
            theme.voteCountZeroColor = color;
            return this;
        }
        
        public Builder setVoteDividerColor(@ColorInt int color) {
            theme.voteDividerColor = color;
            return this;
        }
        
        public Builder setDialogHeaderBackgroundColor(@ColorInt int color) {
            theme.dialogHeaderBackgroundColor = color;
            return this;
        }
        
        public Builder setDialogHeaderTextColor(@ColorInt int color) {
            theme.dialogHeaderTextColor = color;
            return this;
        }
        
        public Builder setOnlineIndicatorColor(@ColorInt int color) {
            theme.onlineIndicatorColor = color;
            return this;
        }
        
        /**
         * Convenience method to set all primary-derived colors at once
         */
        public Builder setAllPrimaryColors(@ColorInt int primary) {
            theme.primaryColor = primary;
            theme.actionButtonColor = primary;
            theme.replyButtonColor = primary;
            theme.toggleRepliesButtonColor = primary;
            theme.loadMoreButtonTextColor = primary;
            return this;
        }
        
        public FastCommentsTheme build() {
            return theme;
        }
    }
    
    // Getters
    @Nullable public Integer getPrimaryColor() { return primaryColor; }
    @Nullable public Integer getPrimaryLightColor() { return primaryLightColor; }
    @Nullable public Integer getPrimaryDarkColor() { return primaryDarkColor; }
    @Nullable public Integer getActionButtonColor() { return actionButtonColor; }
    @Nullable public Integer getReplyButtonColor() { return replyButtonColor; }
    @Nullable public Integer getToggleRepliesButtonColor() { return toggleRepliesButtonColor; }
    @Nullable public Integer getLoadMoreButtonTextColor() { return loadMoreButtonTextColor; }
    @Nullable public Integer getLinkColor() { return linkColor; }
    @Nullable public Integer getLinkColorPressed() { return linkColorPressed; }
    @Nullable public Integer getVoteCountColor() { return voteCountColor; }
    @Nullable public Integer getVoteCountZeroColor() { return voteCountZeroColor; }
    @Nullable public Integer getVoteDividerColor() { return voteDividerColor; }
    @Nullable public Integer getDialogHeaderBackgroundColor() { return dialogHeaderBackgroundColor; }
    @Nullable public Integer getDialogHeaderTextColor() { return dialogHeaderTextColor; }
    @Nullable public Integer getOnlineIndicatorColor() { return onlineIndicatorColor; }
}