package com.fastcomments.sdk;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.net.URI;

public class AvatarFetcher {
    public static void fetchTransformInto(Context context, String avatarSrc, ImageView imageView) {
        Glide.with(context).load(avatarSrc)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView);
    }

    public static void fetchTransformInto(Context context, URI uri, ImageView imageView) {
        Glide.with(context).load(uri)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView);
    }

    public static void fetchTransformInto(Context context, int resourceId, ImageView imageView) {
        Glide.with(context).load(resourceId)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView);
    }
}
