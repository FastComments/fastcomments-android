package com.fastcomments.sdk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Adapter for displaying user mention suggestions
 */
public class MentionSuggestionsAdapter extends ArrayAdapter<UserMention> {

    private final LayoutInflater inflater;

    public MentionSuggestionsAdapter(Context context, List<UserMention> items) {
        super(context, 0, items);
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.mention_suggestion_item, parent, false);
            holder = new ViewHolder();
            holder.avatar = convertView.findViewById(R.id.mentionUserAvatar);
            holder.username = convertView.findViewById(R.id.mentionUsername);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Get the data item for this position
        UserMention mention = getItem(position);
        if (mention != null) {
            // Set the username
            holder.username.setText(mention.getUsername());

            // Load avatar image
            if (mention.getAvatarUrl() != null && !mention.getAvatarUrl().isEmpty()) {
                AvatarFetcher.fetchTransformInto(getContext(), mention.getAvatarUrl(), holder.avatar);
            } else {
                // Set default avatar
                AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, holder.avatar);
            }
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView avatar;
        TextView username;
    }
}