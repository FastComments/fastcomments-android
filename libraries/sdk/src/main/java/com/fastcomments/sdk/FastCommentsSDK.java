package com.fastcomments.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fastcomments.api.DefaultApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.core.FastCommentsSSO;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiClient;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Main SDK class for interacting with FastComments API
 */
public class FastCommentsSDK {

    private static FastCommentsSDK instance;
    private final Context context;
    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;
    private final DefaultApi api;
    private final Executor executor;
    private final Handler mainHandler;

    /**
     * Initialize the FastComments SDK
     * @param context Application context
     * @return FastCommentsSDK instance
     */
    public static synchronized FastCommentsSDK init(Context context) {
        if (instance == null) {
            instance = new FastCommentsSDK(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Get the FastComments SDK instance
     * @return FastCommentsSDK instance
     * @throws IllegalStateException if SDK not initialized
     */
    public static synchronized FastCommentsSDK getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FastCommentsSDK not initialized. Call init() first.");
        }
        return instance;
    }

    private FastCommentsSDK(Context context) {
        this.context = context;
        this.api = new DefaultApi();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Configure the SDK with widget configuration
     * @param config CommentWidgetConfig object
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK configure(CommentWidgetConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Set the current user for SSO authentication
     * @param userInfo User session information
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK setCurrentUser(UserSessionInfo userInfo) {
        this.currentUser = userInfo;
        return this;
    }

    /**
     * Get the current widget configuration
     * @return CommentWidgetConfig object
     */
    public CommentWidgetConfig getConfig() {
        return config;
    }

    /**
     * Get the current user information
     * @return UserSessionInfo object
     */
    public UserSessionInfo getCurrentUser() {
        return currentUser;
    }

    /**
     * Convert SSO configuration to the format expected by the API
     */
    private String convertSSOToBase64(FastCommentsSSO sso) {
        if (sso == null) {
            return null;
        }
        
        StringBuilder ssoString = new StringBuilder();
        
        if (sso.userDataJSONBase64 != null && !sso.userDataJSONBase64.isEmpty()) {
            ssoString.append(sso.userDataJSONBase64).append(".");
        }
        
        if (sso.verificationHash != null && !sso.verificationHash.isEmpty()) {
            ssoString.append(sso.verificationHash).append(".");
        }
        
        if (sso.timestamp != null) {
            ssoString.append(sso.timestamp);
        }
        
        return ssoString.toString();
    }

    /**
     * Callback interface for comment loading
     */
    public interface CommentsCallback {
        void onSuccess(GetCommentsResponseWithPresencePublicComment response);
        void onError(Exception e);
    }

    /**
     * Callback interface for comment posting
     */
    public interface CommentPostCallback {
        void onSuccess(APICommentPublicComment comment);
        void onError(Exception e);
    }

    /**
     * Load comments asynchronously
     * @param callback Callback to receive the response
     */
    public void getComments(final CommentsCallback callback) {
        getComments(null, null, null, true, null, null, callback);
    }

    /**
     * Load comments asynchronously with pagination and threading options
     * @param page The page number (starting from 0)
     * @param skip Number of comments to skip
     * @param limit Maximum number of comments to return
     * @param asTree Whether to return comments in tree structure
     * @param maxTreeDepth Maximum depth of reply tree
     * @param parentId Parent comment ID to fetch replies for
     * @param callback Callback to receive the response
     */
    public void getComments(
            Integer page,
            Integer skip,
            Integer limit,
            Boolean asTree,
            Integer maxTreeDepth,
            String parentId,
            final CommentsCallback callback) {
        
        if (config == null) {
            callback.onError(new IllegalStateException("SDK not configured. Call configure() first."));
            return;
        }
        
        // Convert direction enum if present
        SortDirections direction = config.defaultSortDirection;
        
        // Convert SSO configuration if present
        String ssoParam = null;
        if (config.sso != null) {
            ssoParam = convertSSOToBase64(config.sso);
        }
        
        // If page is not provided, use the one from config
        Double pageParam = null;
        if (page != null) {
            pageParam = page.doubleValue();
        } else if (config.startingPage != null) {
            pageParam = config.startingPage.doubleValue();
        }
        
        // Convert integer parameters to doubles (API expectation)
        Double skipParam = skip != null ? skip.doubleValue() : null;
        Double limitParam = limit != null ? limit.doubleValue() : null;
        Double maxTreeDepthParam = maxTreeDepth != null ? maxTreeDepth.doubleValue() : null;
        
        // For pagination of comments within a parent
        Double skipChildren = null;
        Double limitChildren = config.maxReplyDepth != null ? config.maxReplyDepth.doubleValue() : null;
        
        try {
            // Make the API call asynchronously
            api.getCommentsAsync(
                config.tenantId,
                pageParam,
                direction,
                ssoParam,
                skipParam,
                skipChildren,
                limitParam,
                limitChildren,
                null, // lastGenDate
                null, // fetchPageForCommentId
                true, // includeConfig
                config.countAll, // countAll
                null, // includei10n
                config.locale, // locale
                null, // modules
                null, // isCrawler
                null, // includeNotificationCount
                asTree, // asTree - hierarchical comment structure
                maxTreeDepthParam, // maxTreeDepth
                null, // useFullTranslationIds
                parentId, // parentId - for loading replies to a specific comment
                null, // searchText
                null, // hashTags
                config.userId, // userId
                null, // customConfigStr
                new ApiCallback<GetComments200Response>() {
                    @Override
                    public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                        mainHandler.post(() -> callback.onError(new Exception("API call failed: " + e.getMessage(), e)));
                    }

                    @Override
                    public void onSuccess(GetComments200Response response, int statusCode, Map<String, List<String>> responseHeaders) {
                        mainHandler.post(() -> {
                            if (response.getActualInstance() instanceof GetCommentsResponseWithPresencePublicComment) {
                                GetCommentsResponseWithPresencePublicComment commentsResponse = response.getGetCommentsResponseWithPresencePublicComment();
                                
                                // If the response has a custom config, merge it with our config
                                if (commentsResponse.getCustomConfig() != null) {
                                    config.mergeWith(commentsResponse.getCustomConfig());
                                }
                                
                                callback.onSuccess(commentsResponse);
                            } else if (response.getActualInstance() instanceof APIError) {
                                APIError error = (APIError) response.getActualInstance();
                                callback.onError(new Exception("API Error: " + error.getReason()));
                            } else {
                                callback.onError(new Exception("Unknown response type"));
                            }
                        });
                    }

                    @Override
                    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                        // Not needed for this API call
                    }

                    @Override
                    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                        // Not needed for this API call
                    }
                }
            );
        } catch (ApiException e) {
            mainHandler.post(() -> callback.onError(new Exception("API call failed: " + e.getMessage(), e)));
        }
    }

    /**
     * Create comment data object from the provided parameters
     */
    private CommentData createCommentData(String commentText, String parentId) {
        CommentData commentData = new CommentData();
        commentData.setComment(commentText);
        commentData.setDate(Double.valueOf(new Date().getTime()));
        commentData.setUrlId(config.urlId);
        commentData.setUrl(config.url != null ? config.url : config.urlId);
        
        // Set parent ID if this is a reply
        if (parentId != null && !parentId.isEmpty()) {
            commentData.setParentId(parentId);
        }
        
        // Set user info
        if (currentUser != null) {
            if (currentUser.getActualInstance() instanceof AuthenticatedUserDetails) {
                AuthenticatedUserDetails authenticatedUser = currentUser.getAuthenticatedUserDetails();
                commentData.setUserId(authenticatedUser.getId());
                commentData.setCommenterName(authenticatedUser.getUsername());
                commentData.setAvatarSrc(authenticatedUser.getAvatarSrc());
                commentData.setCommenterEmail(authenticatedUser.getEmail());
            } else if (currentUser.getActualInstance() instanceof AnonUserDetails) {
                AnonUserDetails anonUser = currentUser.getAnonUserDetails();
                commentData.setCommenterName(anonUser.getUsername());
                commentData.setCommenterEmail(anonUser.getEmail());
            }
        }
        
        // Set page title if available
        if (config.pageTitle != null && !config.pageTitle.isEmpty()) {
            commentData.setPageTitle(config.pageTitle);
        }
        
        // Set metadata if available
        if (config.commentMeta != null && !config.commentMeta.isEmpty()) {
            commentData.setMeta(config.commentMeta);
        }
        
        return commentData;
    }

    /**
     * Post a new comment asynchronously
     * @param commentText Comment text content
     * @param parentId Parent comment ID for replies (null for top-level comments)
     * @param callback Callback to receive the posted comment
     */
    public void postComment(String commentText, String parentId, final CommentPostCallback callback) {
        if (config == null) {
            mainHandler.post(() -> callback.onError(new IllegalStateException("SDK not configured. Call configure() first.")));
            return;
        }

        if (currentUser == null) {
            mainHandler.post(() -> callback.onError(new IllegalStateException("User not set. Call setCurrentUser() first.")));
            return;
        }

        if (commentText == null || commentText.isEmpty()) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("Comment text cannot be empty")));
            return;
        }
        
        try {
            // Create comment data
            CommentData commentData = createCommentData(commentText, parentId);
            
            // Convert SSO configuration if present
            String ssoParam = null;
            if (config.sso != null) {
                ssoParam = convertSSOToBase64(config.sso);
            }
            
            // Generate a unique broadcast ID for this comment
            String broadcastId = "android-" + System.currentTimeMillis();
            
            // Make the API call asynchronously
            api.createUserAsync(
                config.tenantId,
                config.urlId,
                broadcastId,
                commentData,
                null, // sessionId
                ssoParam,
                new ApiCallback<CreateUser200Response>() {
                    @Override
                    public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                        mainHandler.post(() -> callback.onError(new Exception("API call failed: " + e.getMessage(), e)));
                    }

                    @Override
                    public void onSuccess(CreateUser200Response response, int statusCode, Map<String, List<String>> responseHeaders) {
                        mainHandler.post(() -> callback.onSuccess(response.getComment()));
                    }

                    @Override
                    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                        // Not needed for this API call
                    }

                    @Override
                    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                        // Not needed for this API call
                    }
                }
            );
        } catch (ApiException e) {
            mainHandler.post(() -> callback.onError(new Exception("API call failed: " + e.getMessage(), e)));
        }
    }

    /**
     * Create a CommentWidgetConfig with the specified tenant ID and URL ID
     * @param tenantId Your FastComments tenant ID
     * @param urlId URL ID for the comment thread
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfig(String tenantId, String urlId) {
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = tenantId;
        config.urlId = urlId;
        return config;
    }

    /**
     * Create a CommentWidgetConfig with SSO enabled
     * @param tenantId Your FastComments tenant ID
     * @param urlId URL ID for the comment thread
     * @param sso SSO configuration
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfigWithSSO(String tenantId, String urlId, FastCommentsSSO sso) {
        CommentWidgetConfig config = createConfig(tenantId, urlId);
        config.sso = sso;
        return config;
    }
}