package com.fastcomments.sdk.examples;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that launches the system image picker.
 * In a real implementation, the result would be handled via an ActivityResultLauncher
 * and the selected image uploaded to your server, then inserted as an img tag.
 */
public class ImagePickerToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "image_picker";

    @Override
    public int getIconResourceId() {
        return android.R.drawable.ic_menu_gallery;
    }

    @Override
    public int getContentDescriptionResourceId() {
        return R.string.insert_image;
    }

    @Override
    public String getBadgeText() {
        return null;
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        Context context = buttonView.getContext();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (context instanceof Activity) {
            ((Activity) context).startActivityForResult(Intent.createChooser(intent, "Select Image"), 1001);
        }
    }

    @Override
    public String getId() {
        return BUTTON_ID;
    }
}
