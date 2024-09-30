package com.longyb.mylive.server.handlers;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.longyb.mylive.server.handlers.http.AbstractHttpRouterHandler;
import com.longyb.mylive.server.handlers.http.GetFlvStreamHandler;
import com.longyb.mylive.server.handlers.http.GetStreamsHandler;
import com.longyb.mylive.server.manager.StreamManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class HttpRouterHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final StreamManager streamManager;

    private final Map<String, AbstractHttpRouterHandler> httpHandlers =  new HashMap<>();
    private final Map<String, Pattern> routePatterns = new HashMap<>();

    public HttpRouterHandler(StreamManager streamManager) {
        this.streamManager = streamManager;

        // Define routes with {} placeholders and corresponding handlers
        httpHandlers.put("/api/stream/flv/{app}/{stream}", new GetFlvStreamHandler(streamManager));
        httpHandlers.put("/api/stream/flv/{app}/{stream}.flv", new GetFlvStreamHandler(streamManager));
        httpHandlers.put("/api/streams", new GetStreamsHandler(streamManager));

        // Compile patterns for routes
        for (String route : httpHandlers.keySet()) {
            routePatterns.put(route, createPatternFromRoute(route));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest req) {
            String uri = req.uri();
            log.info("API: Requested URL {}", uri);

            boolean matched = false;

            for (Map.Entry<String, Pattern> entry : routePatterns.entrySet()) {
                Matcher matcher = entry.getValue().matcher(uri);
                if (matcher.matches()) {
                    // Extract path variables
                    Map<String, String> pathVariables = extractPathVariables(matcher, entry.getKey());

                    try {
                        // Call the appropriate handler
                        AbstractHttpRouterHandler handler = httpHandlers.get(entry.getKey());
                        handler.handle(ctx, req, pathVariables);
                        matched = true;
                    } catch (Exception e) {
                        // Handle unexpected exceptions with a 500 error response
                        log.error("Internal server error: ", e);
                        handle500(ctx, e.getMessage());
                        return;
                    }
                    break;
                }
            }

            if (!matched) {
                // Handle 404 Not Found
                handle404(ctx, uri);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    // Utility method to create a 404 Not Found response
    private void handle404(ChannelHandlerContext ctx, String uri) {
        String notFoundMessage = "404 Not Found: The requested URL [" + uri + "] was not found on this server.";
        ByteBuf content = Unpooled.copiedBuffer(notFoundMessage, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        // Write the response and close the connection
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    // Utility method to create a 500 Internal Server Error response
    private void handle500(ChannelHandlerContext ctx, String errorMessage) {
        String internalErrorMessage = "500 Internal Server Error: " + (errorMessage != null ? errorMessage : "An unexpected error occurred.");
        ByteBuf content = Unpooled.copiedBuffer(internalErrorMessage, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        // Write the response and close the connection
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    // Utility method to create a regex pattern from a route with {}
    private Pattern createPatternFromRoute(String route) {
        String regex = route.replaceAll("\\{\\w+\\}", "([^/]+)"); // Replace {variable} with a capturing group
        return Pattern.compile(regex);
    }

    // Utility method to extract path variables based on route
    private Map<String, String> extractPathVariables(Matcher matcher, String route) {
        Map<String, String> pathVariables = new HashMap<>();
        Pattern variablePattern = Pattern.compile("\\{(\\w+)\\}");
        Matcher variableMatcher = variablePattern.matcher(route);

        int groupIndex = 1; // Start from group 1 because group 0 is the entire match
        while (variableMatcher.find()) {
            String variableName = variableMatcher.group(1);
            pathVariables.put(variableName, matcher.group(groupIndex));
            groupIndex++;
        }

        return pathVariables;
    }
}
