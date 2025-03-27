package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.fastcomments.model.CommentUserBadgeInfo;

/**
 * Dialog to display when a badge is awarded to the current user
 */
public class BadgeAwardDialog {

    private Dialog dialog;
    private final Context context;

    public BadgeAwardDialog(Context context) {
        this.context = context;
    }

    /**
     * Show the badge award dialog
     *
     * @param badge The badge that was awarded
     */
    public void show(CommentUserBadgeInfo badge) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        
        View view = LayoutInflater.from(context).inflate(R.layout.badge_award_dialog, null);
        dialog.setContentView(view);
        
        // Set up dialog views
        TextView badgeText = view.findViewById(R.id.badgeDialogText);
        ImageView badgeIcon = view.findViewById(R.id.badgeDialogIcon);
        TextView badgeDescription = view.findViewById(R.id.badgeDialogDescription);
        Button closeButton = view.findViewById(R.id.badgeDialogCloseButton);
        
        // Set badge text (display label or fallback to description)
        String displayText = badge.getDisplayLabel() != null ? badge.getDisplayLabel() : badge.getDescription();
        badgeText.setText(displayText);
        
        // Set badge description 
        badgeDescription.setText(badge.getDescription());
        
        // Apply custom colors if provided
        if (badge.getBackgroundColor() != null) {
            try {
                badgeText.setBackgroundColor(Color.parseColor(badge.getBackgroundColor()));
            } catch (IllegalArgumentException e) {
                // Use default background if color is invalid
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
        
        // Set close button action
        closeButton.setOnClickListener(v -> dismiss());
        
        dialog.show();
    }
    
    /**
     * Dismiss the dialog
     */
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}