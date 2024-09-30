
package com.longyb.mylive.server.handlers.http;

import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;
import com.longyb.mylive.server.manager.StreamManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.util.Map;

public class GetFlvStreamHandler extends AbstractHttpRouterHandler {


    public GetFlvStreamHandler(StreamManager streamManager) {
        super(streamManager);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, HttpRequest req, Map<String, String> pathVariables) throws Exception {
        String uri = req.uri();

        String app = pathVariables.get("app");
        String streamName = pathVariables.get("stream");

        if (streamName.endsWith(".flv")) {
            streamName = streamName.substring(0, streamName.length() - ".flv".length());
        }

        StreamName sn = new StreamName(app, streamName, false);
        Stream stream = getStreamManager().getStream(sn);

        if (stream == null) {
            httpResponseStreamNotExist(ctx, uri);
            return;
        }

        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "video/x-flv");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response);
        stream.addHttpFlvSubscriber(ctx.channel());
    }


    private void httpResponseStreamNotExist(ChannelHandlerContext ctx, String uri) {
        ByteBuf body = Unpooled.wrappedBuffer(("stream [" + uri + "] not exist").getBytes());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
