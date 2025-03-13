package com.fastcomments.sdk;

import android.content.Context;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.APICommentPublicComment;
import com.fastcomments.model.UserSessionInfo;

import java.util.List;

public class FastCommentsSDK {

    private Context context;
    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;

    public FastCommentsSDK(Context context) {
        this.context = context;
    }

    public void loadForConfig(CommentWidgetConfig config) {
        this.config = config;
    }
}
