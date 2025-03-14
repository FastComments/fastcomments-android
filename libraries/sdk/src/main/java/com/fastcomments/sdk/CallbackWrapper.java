package com.fastcomments.sdk;

import android.os.Handler;

import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.APIError;

import java.util.List;
import java.util.Map;

public class CallbackWrapper<T> {
    public ApiCallback<T> wrap(Handler mainHandler, FCCallback<T> prevCB, FCCallback<T> callback) {
        return new ApiCallback<T>() {
            @Override
            public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                final APIError error = new APIError();
                error.setStatusCode((double) i);
                error.setCode("internal");
                mainHandler.post(() -> {
                    if (!callback.onFailure(error)) {
                        prevCB.onFailure(error);
                    }
                });
            }

            @Override
            public void onSuccess(T o, int i, Map<String, List<String>> map) {
                mainHandler.post(() -> {
                    if (o instanceof APIError) {
                        final APIError error = (APIError) o;
                        if (!callback.onFailure(error)) {
                            prevCB.onFailure(error);
                        }
                    } else {
                        if (!callback.onSuccess(o)) {
                            prevCB.onSuccess(o);
                        }
                    }
                });
            }

            @Override
            public void onUploadProgress(long l, long l1, boolean b) {
                prevCB.onUploadProgress(l, l1, b);
            }

            @Override
            public void onDownloadProgress(long l, long l1, boolean b) {
                prevCB.onDownloadProgress(l, l1, b);
            }
        };
    }

    public static void handleAPIException(Handler handler, FCCallback<?> cb, ApiException e) {
        final APIError error = new APIError();
        error.setCode("internal");
        error.setReason(e.getMessage() != null ? e.getMessage() : "N/A");
        error.setStatusCode((double) e.getCode());
        handler.post(() -> {
            cb.onFailure(error);
        });
    }
}
