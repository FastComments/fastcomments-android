package com.fastcomments.sdk;

import androidx.annotation.Nullable;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.FeedPost;

public class UserClickContext {
    
    @Nullable
    private final PublicComment comment;
    
    @Nullable
    private final FeedPost feedPost;
    
    private UserClickContext(@Nullable PublicComment comment, @Nullable FeedPost feedPost) {
        this.comment = comment;
        this.feedPost = feedPost;
    }
    
    public static UserClickContext fromComment(PublicComment comment) {
        return new UserClickContext(comment, null);
    }
    
    public static UserClickContext fromFeedPost(FeedPost feedPost) {
        return new UserClickContext(null, feedPost);
    }
    
    @Nullable
    public PublicComment getComment() {
        return comment;
    }
    
    @Nullable
    public FeedPost getFeedPost() {
        return feedPost;
    }
    
    public boolean isComment() {
        return comment != null;
    }
    
    public boolean isFeedPost() {
        return feedPost != null;
    }
}