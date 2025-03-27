package com.fastcomments.sdk;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.UserSessionInfo;

/**
 * Dialog for collecting user credentials when voting/commenting as an anonymous user
 */
public class UserLoginDialog {

    /**
     * Callback interface for dialog actions
     */
    public interface OnUserCredentialsListener {
        void onUserCredentialsEntered(String username, String email);
        void onCancel();
    }

    /**
     * Show login dialog for anonymous users
     * 
     * @param context Context for dialog
     * @param config FastComments widget config
     * @param action The action being performed ("vote" or "comment")
     * @param listener Callback for dialog actions
     */
    public static void show(Context context, CommentWidgetConfig config, String action, OnUserCredentialsListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        
        // Inflate custom dialog layout
        View dialogView = inflater.inflate(R.layout.dialog_user_login, null);
        builder.setView(dialogView);
        
        // Get dialog elements
        TextView titleText = dialogView.findViewById(R.id.dialogTitle);
        TextView messageText = dialogView.findViewById(R.id.dialogMessage);
        EditText usernameInput = dialogView.findViewById(R.id.usernameInput);
        EditText emailInput = dialogView.findViewById(R.id.emailInput);
        Button submitButton = dialogView.findViewById(R.id.submitButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        TextView anonNoticeText = dialogView.findViewById(R.id.anonNoticeText);
        
        // Set title based on action
        titleText.setText(action.equals("vote") ? 
                R.string.vote_login_title : 
                R.string.comment_login_title);
                
        // Show anonymous notice if anonymous actions are allowed
        boolean allowAnon = action.equals("vote") ? 
                Boolean.TRUE.equals(config.allowAnonVotes) : 
                Boolean.TRUE.equals(config.allowAnon);
                
        if (allowAnon) {
            anonNoticeText.setVisibility(View.VISIBLE);
            anonNoticeText.setText(action.equals("vote") ? 
                    R.string.vote_anon_notice : 
                    R.string.comment_anon_notice);
        } else {
            anonNoticeText.setVisibility(View.GONE);
        }
        
        // Set default username if available
        if (config.defaultUsername != null && !config.defaultUsername.isEmpty()) {
            usernameInput.setText(config.defaultUsername);
        }
        
        // Hide email input if emails are disabled
        if (Boolean.TRUE.equals(config.disableEmailInputs)) {
            emailInput.setVisibility(View.GONE);
            messageText.setText(R.string.enter_username_message);
        } else {
            messageText.setText(R.string.enter_username_email_message);
        }
        
        // Create and configure dialog
        final AlertDialog dialog = builder.create();
        
        // Submit button click handler
        submitButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            
            // Validate inputs
            if (TextUtils.isEmpty(username)) {
                usernameInput.setError(context.getString(R.string.username_required));
                return;
            }
            
            // Email validation (only if email inputs not disabled and anon not allowed)
            if (!Boolean.TRUE.equals(config.disableEmailInputs) && !allowAnon && TextUtils.isEmpty(email)) {
                emailInput.setError(context.getString(R.string.email_required));
                return;
            }
            
            dialog.dismiss();
            listener.onUserCredentialsEntered(username, email);
        });
        
        // Cancel button click handler
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onCancel();
        });
        
        dialog.show();
    }
}