package com.longyb.mylive.server.handlers;

import com.longyb.mylive.amf.Amf0Object;
import com.longyb.mylive.server.entities.Role;
import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;
import com.longyb.mylive.server.manager.StreamManager;
import com.longyb.mylive.server.rtmp.Constants;
import com.longyb.mylive.server.rtmp.messages.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rtmp messages are handled here
 *
 * @author longyubo
 * @version 2019年12月18日 下午11:10:26
 */
@Slf4j
public class RtmpMessageHandler extends SimpleChannelInboundHandler<RtmpMessage> {

    private int ackWindowSize;
    private int lastSentbackSize;
    private int bytesReceived;

    private Role role;
    private boolean normalShutdown;

    private StreamName streamName;

    private final StreamManager streamManager;

    public RtmpMessageHandler(StreamManager manager) {
        this.streamManager = manager;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!normalShutdown && role == Role.Publisher) {
            Stream stream = streamManager.getStream(streamName);
            if (stream != null) {
                stream.sendEofToAllSubscriberAndClose();
            } else {
                log.error("channel inactive but stream:{} is null", streamName);
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RtmpMessage msg) {

        maySendAck(ctx, msg);
        if (!(msg instanceof VideoMessage || msg instanceof AudioMessage)) {
            log.info("RTMP_Message_Read : {}", msg);
        }
        if (msg instanceof WindowAcknowledgementSize windowAcknowledgementSize) {
            this.ackWindowSize = windowAcknowledgementSize.getWindowSize();
            return;
        }

        if (msg instanceof RtmpCommandMessage rtmpCommandMessage) {
            handleCommand(ctx, rtmpCommandMessage);
        } else if (msg instanceof RtmpDataMessage rtmpDataMessage) {
            handleDataMessage(ctx, rtmpDataMessage);
        } else if (msg instanceof RtmpMediaMessage rtmpMediaMessage) {
            handleMedia(ctx, rtmpMediaMessage);
        } else if (msg instanceof UserControlMessageEvent userControlMessageEvent) {
            handleUserControl(ctx, userControlMessageEvent);
        }
    }

    private void handleUserControl(ChannelHandlerContext ctx, UserControlMessageEvent msg) {
//		boolean isBufferLength = msg.isBufferLength();
//		if (isBufferLength) {
//			if (role == Role.Subscriber) {
//				startPlay(ctx, streamManager.getStream(streamName));
//			}
//		}

    }

    private void handleMedia(ChannelHandlerContext ctx, RtmpMediaMessage msg) {
        Stream stream = streamManager.getStream(streamName);
        if (stream == null) {
            log.error("stream: {} does not exist!", streamName);
            return;
        }
        stream.addContent(msg);
    }

    private void handleDataMessage(ChannelHandlerContext ctx, RtmpDataMessage msg) {

        String name = (String) msg.getData().get(0);
        if ("@setDataFrame".equals(name)) {
            // save on metadata
            Map<String, Object> properties = (Map<String, Object>) msg.getData().get(2);
            properties.remove("filesize");

            String encoder = (String) properties.get("encoder");
            if (encoder != null && encoder.contains("obs")) {
                streamName.setObsClient(true);
            }
            Stream stream = streamManager.getStream(streamName);
            stream.setMetadata(properties);
        }

    }

    private void handleCommand(ChannelHandlerContext ctx, RtmpCommandMessage msg) {
        List<Object> command = msg.getCommand();
        String commandName = (String) command.get(0);
        switch (commandName) {
            case "connect" -> handleConnect(ctx, msg);
            case "createStream" -> handleCreateStream(ctx, msg);
            case "publish" -> handlePublish(ctx, msg);
            case "play" -> handlePlay(ctx, msg);
            case "deleteStream", "closeStream" -> handleCloseStream(ctx, msg);
            default -> {
            }
        }

    }

    private void handleCloseStream(ChannelHandlerContext ctx, RtmpCommandMessage msg) {
        if (role == Role.Subscriber) {
            log.info("one subscriber delete stream.do nothing");
            return;
        }
        // send back 'NetStream.Unpublish.Success' to publisher
        RtmpCommandMessage onStatus = onStatus("status", "NetStream.Unpublish.Success", "Stop publishing");
        ctx.write(onStatus);
        // send User Control Message Stream EOF (1) to all subscriber
        // and we close all publisher and subscribers
        Stream stream = streamManager.getStream(streamName);
        if (stream == null) {
            log.error("can't find stream:{} in stream manager", streamName);
        } else {
            stream.sendEofToAllSubscriberAndClose();
            streamManager.remove(streamName);
            normalShutdown = true;
            ctx.close();

        }
    }

    private void handlePlay(ChannelHandlerContext ctx, RtmpCommandMessage msg) {
        role = Role.Subscriber;

        String name = (String) msg.getCommand().get(3);
        streamName.setName(name);

        Stream stream = streamManager.getStream(streamName);
        if (stream == null) {
            log.info("client play request for stream:{} but not exist.", streamName);
            // NetStream.Play.StreamNotFound
            RtmpCommandMessage onStatus = onStatus("error", "NetStream.Play.StreamNotFound", "No Such Stream");

            ctx.writeAndFlush(onStatus);

            normalShutdown = true;
            ctx.channel().close();

        } else {
            startPlay(ctx, stream);
        }

        // real play happens when setBuffer

    }

    private void startPlay(ChannelHandlerContext ctx, Stream stream) {

        ctx.writeAndFlush(UserControlMessageEvent.streamBegin(Constants.DEFAULT_STREAM_ID));

        RtmpCommandMessage onStatus = onStatus("status", "NetStream.Play.Start", "Start live");

        ctx.writeAndFlush(onStatus);

        List<Object> args = new ArrayList<>();
        args.add("|RtmpSampleAccess");
        args.add(true);
        args.add(true);
        RtmpCommandMessage rtmpSampleAccess = new RtmpCommandMessage(args);

        ctx.writeAndFlush(rtmpSampleAccess);

        List<Object> metadata = new ArrayList<>();
        metadata.add("onMetaData");
        metadata.add(stream.getMetadata());
        RtmpDataMessage msgMetadata = new RtmpDataMessage(metadata);

        ctx.writeAndFlush(msgMetadata);

//		AudioMessage emptt = new AudioMessage(Unpooled.EMPTY_BUFFER);
//		ctx.writeAndFlush(emptt);

        stream.addSubscriber(ctx.channel());
    }

    private void handlePublish(ChannelHandlerContext ctx, RtmpCommandMessage msg) {
        log.info("publish :{}", msg);
        role = Role.Publisher;

        String streamType = (String) msg.getCommand().get(4);
        if (!"live".equals(streamType)) {
            log.error("unsupport stream type :{}", streamType);
            ctx.channel().disconnect();
        }

        String name = (String) msg.getCommand().get(3);
        streamName.setName(name);
        streamName.setApp(streamType);

        createStream(ctx);
        // reply a onStatus
        RtmpCommandMessage onStatus = onStatus("status", "NetStream.Publish.Start", "Start publishing");

        ctx.writeAndFlush(onStatus);

    }

    private void createStream(ChannelHandlerContext ctx) {
        Stream s = new Stream(streamName);
        s.setPublisher(ctx.channel());
        streamManager.newStream(streamName, s);
    }

    private void handleCreateStream(ChannelHandlerContext ctx, RtmpCommandMessage msg) {

        log.info("create stream received : {}", msg);

        List<Object> result = new ArrayList<>();
        result.add("_result");
        result.add(msg.getCommand().get(1));// transaction id
        result.add(null);// properties
        result.add(Constants.DEFAULT_STREAM_ID);// stream id

        RtmpCommandMessage response = new RtmpCommandMessage(result);

        ctx.writeAndFlush(response);

    }

    private void handleConnect(ChannelHandlerContext ctx, RtmpCommandMessage msg) {

        // client send connect
        // server reply windows ack size and set peer bandwidth

        log.info("client connected {} ", msg);

        String app = (String) ((Map<?, ?>) msg.getCommand().get(2)).get("app");
        Integer clientRequestEncode = (Integer) ((Map<?, ?>) msg.getCommand().get(2)).get("objectEncoding");
        if (clientRequestEncode != null && clientRequestEncode == 3) {
            log.error("client :{} request AMF3 encoding but server currently doesn't support", ctx);
            ctx.close();
            return;
        }

        streamName = new StreamName(app, null, false);

        int ackSize = 5000000;
        WindowAcknowledgementSize was = new WindowAcknowledgementSize(ackSize);

        SetPeerBandwidth spb = new SetPeerBandwidth(ackSize, Constants.SET_PEER_BANDWIDTH_TYPE_SOFT);

        SetChunkSize setChunkSize = new SetChunkSize(5000);

        ctx.writeAndFlush(was);
        ctx.writeAndFlush(spb);
        ctx.writeAndFlush(setChunkSize);

        List<Object> result = new ArrayList<>();
        result.add("_result");
        result.add(msg.getCommand().get(1));// transaction id
        result.add(new Amf0Object().addProperty("fmsVer", "FMS/3,0,1,123").addProperty("capabilities", 31));
        result.add(new Amf0Object().addProperty("level", "status").addProperty("code", "NetConnection.Connect.Success")
                .addProperty("description", "Connection succeeded").addProperty("objectEncoding", 0));

        RtmpCommandMessage response = new RtmpCommandMessage(result);

        ctx.writeAndFlush(response);

    }

    private void maySendAck(ChannelHandlerContext ctx, RtmpMessage msg) {

        // we need ack when receive bytes greater than ack window
        // this is not an accurate implement
        int receiveBytes = msg.getInboundBodyLength() + msg.getInboundHeaderLength();
        bytesReceived += receiveBytes;

        if (ackWindowSize <= 0) {
            return;
        }
        // bytes received may overflow at ~2GB
        // we need reset here
        if (bytesReceived > 0X70000000) {
            log.warn("reset bytesReceived in case of overflow.");
            ctx.writeAndFlush(new Acknowledgement(bytesReceived));
            bytesReceived = 0;
            lastSentbackSize = 0;
            return;
        }

        if (bytesReceived - lastSentbackSize >= ackWindowSize) {
            // write an ack to client
            lastSentbackSize = bytesReceived;
            ctx.writeAndFlush(new Acknowledgement(lastSentbackSize));
        }
    }

    public RtmpCommandMessage onStatus(String level, String code, String description) {
        List<Object> result = new ArrayList<>();
        result.add("onStatus");
        result.add(0);// always 0
        result.add(null);// properties
        result.add(new Amf0Object().addProperty("level", level).addProperty("code", code).addProperty("description",
                description));// stream id

        return new RtmpCommandMessage(result);
    }

}
