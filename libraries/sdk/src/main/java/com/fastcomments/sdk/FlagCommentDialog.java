package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

/**
 * Dialog to flag a comment
 */
public class FlagCommentDialog {

    private Dialog dialog;
    private final Context context;
    private Callback<String> onSubmitCallback;

    public FlagCommentDialog(Context context) {
        this.context = context;
    }

    /**
     * Set the callback to be called when the flag is submitted
     *
     * @param callback The callback to call with the flag reason
     * @return This dialog for chaining
     */
    public FlagCommentDialog setOnSubmitCallback(Callback<String> callback) {
        this.onSubmitCallback = callback;
        return this;
    }

    /**
     * Show the flag dialog
     */
    public void show() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        
        View view = LayoutInflater.from(context).inflate(R.layout.flag_comment_dialog, null);
        dialog.setContentView(view);
        
        // Set up dialog views
        EditText flagReasonText = view.findViewById(R.id.flagReasonText);
        Button cancelButton = view.findViewById(R.id.cancelFlagButton);
        Button submitButton = view.findViewById(R.id.submitFlagButton);
        
        // Set cancel button action
        cancelButton.setOnClickListener(v -> dismiss());
        
        // Set submit button action
        submitButton.setOnClickListener(v -> {
            String reason = flagReasonText.getText().toString().trim();
            if (!reason.isEmpty() && onSubmitCallback != null) {
                onSubmitCallback.call(reason);
                dismiss();
            }
        });
        
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