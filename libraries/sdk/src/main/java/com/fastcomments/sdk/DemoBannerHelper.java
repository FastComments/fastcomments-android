package com.fastcomments.sdk;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

/**
 * Helper class for setting up demo banners across SDK views
 */
public class DemoBannerHelper {
    
    /**
     * Sets up the demo banner if tenant ID is "demo"
     * 
     * @param containerView The view containing the demo banner
     * @param sdk The SDK instance to check tenant ID
     */
    public static void setupDemoBanner(View containerView, FastCommentsSDK sdk) {
        String tenantId = sdk != null ? sdk.getConfig().tenantId : null;
        setupDemoBannerInternal(containerView, tenantId);
    }
    
    /**
     * Sets up the demo banner for feed views with feed SDK
     * 
     * @param containerView The view containing the demo banner
     * @param feedSdk The feed SDK instance to check tenant ID
     */
    public static void setupDemoBanner(View containerView, FastCommentsFeedSDK feedSdk) {
        String tenantId = feedSdk != null ? feedSdk.getConfig().tenantId : null;
        setupDemoBannerInternal(containerView, tenantId);
    }
    
    /**
     * Internal method to handle the common demo banner setup logic
     * 
     * @param containerView The view containing the demo banner
     * @param tenantId The tenant ID to check
     */
    private static void setupDemoBannerInternal(View containerView, String tenantId) {
        View demoBanner = containerView.findViewById(R.id.demoBanner);
        if (demoBanner != null) {
            // Show banner only if tenant ID is "demo"
            if ("demo".equals(tenantId)) {
                demoBanner.setVisibility(View.VISIBLE);
                
                // Set up click listener for "Create an account" link
                TextView createAccountLink = demoBanner.findViewById(R.id.createAccountLink);
                if (createAccountLink != null) {
                    createAccountLink.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://fastcomments.com"));
                        containerView.getContext().startActivity(intent);
                    });
                }
            } else {
                demoBanner.setVisibility(View.GONE);
            }
        }
    }
}