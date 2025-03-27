package com.fastcomments.sdk;

import com.fastcomments.model.APIError;

public interface FCCallback<ResponseType> {
    public static final boolean CONSUME = true;
    public static final boolean CONTINUE = true;

    /**
     * @return true to stop the callback chain
     */
    boolean onFailure(APIError error);

    /**
     * @return true to stop the callback chain
     */

    boolean onSuccess(ResponseType response);

    default void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
        // no-op
    }

    default void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
        // no-op
    }
}
