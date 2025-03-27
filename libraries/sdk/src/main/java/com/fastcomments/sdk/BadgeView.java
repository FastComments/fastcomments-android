package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.fastcomments.model.CommentUserBadgeInfo;

/**
 * Helper class to handle rendering badges in the FastComments UI
 */
public class BadgeView {

    /**
     * Create a badge view based on badge information
     * 
     * @param context The context
     * @param badge The badge information
     * @return The badge view
     */
    public static View createBadgeView(Context context, CommentUserBadgeInfo badge) {
        View badgeView = LayoutInflater.from(context).inflate(R.layout.badge_item, null);
        
        // Get references to the badge view components
        ImageView badgeIcon = badgeView.findViewById(R.id.badgeIcon);
        TextView badgeText = badgeView.findViewById(R.id.badgeText);
        LinearLayout badgeContainer = (LinearLayout)badgeView;
        
        // Set badge text
        String displayText = badge.getDisplayLabel() != null ? badge.getDisplayLabel() : badge.getDescription();
        badgeText.setText(displayText);
        
        // Apply colors if provided
        if (badge.getBackgroundColor() != null) {
            try {
                badgeContainer.setBackgroundColor(Color.parseColor(badge.getBackgroundColor()));
            } catch (IllegalArgumentException e) {
                // Use default background if color is invalid
            }
        }
        
        if (badge.getBorderColor() != null) {
            try {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(1, 1, 1, 1);
                badgeContainer.setPadding(badgeContainer.getPaddingLeft() + 1, 
                        badgeContainer.getPaddingTop() + 1,
                        badgeContainer.getPaddingRight() + 1, 
                        badgeContainer.getPaddingBottom() + 1);
                badgeContainer.setBackgroundColor(Color.parseColor(badge.getBorderColor()));
            } catch (IllegalArgumentException e) {
                // Ignore invalid border color
            }
        }
        
        if (badge.getTextColor() != null) {
            try {
                badgeText.setTextColor(Color.parseColor(badge.getTextColor()));
            } catch (IllegalArgumentException e) {
                // Use default text color if color is invalid
            }
        }
        
        // Load badge icon if available
        if (badge.getDisplaySrc() != null && !badge.getDisplaySrc().isEmpty()) {
            badgeIcon.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(badge.getDisplaySrc())
                    .into(badgeIcon);
        } else {
            badgeIcon.setVisibility(View.GONE);
        }
        
        return badgeView;
    }
}