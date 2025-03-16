package com.fastcomments.sdk;

import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsTree {

    public Map<String, RenderableComment> commentsById;
    public List<RenderableComment> comments;
    private RecyclerView.Adapter<?> adapter;

    public CommentsTree() {
        this.commentsById = new HashMap<>(30);
        this.comments = new ArrayList<>(30);
    }

    public void setAdapter(RecyclerView.Adapter<?> adapter) {
        this.adapter = adapter;
    }

    public void build(List<PublicComment> comments) {
        this.comments = RenderableComment.transformTreeToUIRenderableList(commentsById, comments);
    }

    public int visibleSize() {
        return comments.size();
    }

    public void update() {
        // TODO
        // TODO atomic updates to adapter
    }
}
