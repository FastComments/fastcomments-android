package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;

import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.APIError;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CallbackWrapperTest {

    @Test
    public void errorBodyKeepsTheServerCode() {
        ApiException e = new ApiException(
                401,
                Collections.emptyMap(),
                "{\"status\":\"failed\",\"code\":\"poll-login-required\",\"reason\":\"Log in to vote.\"}");
        APIError error = CallbackWrapper.createErrorFromException(e);
        assertEquals("poll-login-required", error.getCode());
        assertEquals("Log in to vote.", error.getReason());
    }

    @Test
    public void errorBodyWithTranslatedErrorAndConfig() {
        ApiException e = new ApiException(
                429,
                Collections.emptyMap(),
                "{\"status\":\"failed\",\"code\":\"rate-limited\",\"reason\":\"Slow down\","
                        + "\"translatedError\":\"Please wait\",\"customConfig\":{\"translations\":{\"t1\":\"x\"}}}");
        APIError error = CallbackWrapper.createErrorFromException(e);
        assertEquals("rate-limited", error.getCode());
        assertEquals("Please wait", error.getTranslatedError());
    }
}
