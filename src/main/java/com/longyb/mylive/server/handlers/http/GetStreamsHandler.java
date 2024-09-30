package com.longyb.mylive.server.handlers.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;
import com.longyb.mylive.server.manager.StreamManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetStreamsHandler extends AbstractHttpRouterHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GetStreamsHandler(StreamManager streamManager) {
        super(streamManager);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, HttpRequest req, Map<String, String> pathVariables) throws Exception {
        // Retrieve all streams from the manager
        Map<StreamName, Stream> streams = getStreamManager().getAllStreams();

        // Convert to a list of GetStreamsResponseItem
        List<GetStreamsResponseItem> responseItems = streams.keySet().stream()
                .map(stream -> new GetStreamsResponseItem(stream.getApp(), stream.getName()))
                .toList();

        // Serialize to JSON
        String jsonResponse = objectMapper.writeValueAsString(responseItems);

        // Create HTTP response
        ByteBuf content = Unpooled.copiedBuffer(jsonResponse, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        // Write the response and close the connection
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Data
    @AllArgsConstructor
    private static class GetStreamsResponseItem {
        @JsonProperty("application")
        private String application;

        @JsonProperty("stream")
        private String stream;
    }
}
