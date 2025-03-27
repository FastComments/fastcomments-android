package com.fastcomments.sdk;

public interface Producer<Input, Output> {
    void get(Input input, Callback<Output> cb);
}
