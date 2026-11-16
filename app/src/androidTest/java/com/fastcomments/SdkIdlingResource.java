package com.fastcomments;

import androidx.test.espresso.IdlingResource;
import com.fastcomments.sdk.FastCommentsSDK;

/**
 * Busy while the SDK has API calls in flight, so Espresso waits on its requests instead of sleeping.
 */
final class SdkIdlingResource implements IdlingResource {

    private final String name;
    private final FastCommentsSDK sdk;
    private volatile ResourceCallback callback;

    SdkIdlingResource(String name, FastCommentsSDK sdk) {
        this.name = name;
        this.sdk = sdk;
        sdk.setApiIdleCallback(() -> {
            ResourceCallback current = callback;
            if (current != null) {
                current.onTransitionToIdle();
            }
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isIdleNow() {
        return sdk.isApiIdle();
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.callback = callback;
    }
}
