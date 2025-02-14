package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fastcomments.client.R;

public class CommentFormView extends LinearLayout {

    private ImageView avatarImageView;
    private TextView userNameTextView;
    private EditText commentEditText;
    private Button submitButton;

    public CommentFormView(Context context) {
        super(context);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.comment_form_view, this, true);
        avatarImageView = findViewById(R.id.formAvatar);
        userNameTextView = findViewById(R.id.formUserName);
        commentEditText = findViewById(R.id.formEditText);
        submitButton = findViewById(R.id.formSubmitButton);

        submitButton.setOnClickListener(v -> {
            // Invoke the SDK API to post the comment.
            // You may pass an identifier if this is a reply to a specific comment.
        });
    }

//    public void setCurrentUser(User user) {
//        userNameTextView.setText(user.getName());
//    }
}
