package com.longyb.mylive.server.handlers.http;

import com.longyb.mylive.server.manager.StreamManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;

public abstract class AbstractHttpRouterHandler {

    @Getter(AccessLevel.PROTECTED)
    private final StreamManager streamManager;


    protected AbstractHttpRouterHandler(StreamManager streamManager) {
        this.streamManager = streamManager;
    }

    public abstract void handle(ChannelHandlerContext ctx, HttpRequest req, Map<String, String> pathVariables) throws Exception;


}
