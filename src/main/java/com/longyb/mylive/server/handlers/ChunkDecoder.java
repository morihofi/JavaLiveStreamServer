package com.longyb.mylive.server.handlers;

import com.longyb.mylive.server.rtmp.RtmpMessageDecoder;
import com.longyb.mylive.server.rtmp.messages.RtmpMessage;
import com.longyb.mylive.server.rtmp.messages.SetChunkSize;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

import static com.longyb.mylive.server.rtmp.Constants.*;

/**
 * @author longyubo
 * @version 2019年12月14日 下午3:40:18
 */
@Slf4j
public class ChunkDecoder extends ReplayingDecoder<DecodeState> {

    // changed by client command
    int clientChunkSize = 128;

    HashMap<Integer/* csid */, RtmpHeader> previousHeaders = HashMap.newHashMap(4);
    HashMap<Integer/* csid */, ByteBuf> inCompletePayload = HashMap.newHashMap(4);

    ByteBuf currentPayload = null;
    int currentCsid;

    int ackWindowSize = -1;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {

        DecodeState state = state();

        if (state == null) {
            state(DecodeState.STATE_HEADER);
        }
        if (state == DecodeState.STATE_HEADER) {
            RtmpHeader rtmpHeader = readHeader(in);
            log.debug("rtmpHeader read:{}", rtmpHeader);

            completeHeader(rtmpHeader);
            currentCsid = rtmpHeader.getCsid();

            // initialize the payload
            if (rtmpHeader.getFmt() != CHUNK_FMT_3) {
                ByteBuf buffer = Unpooled.buffer(rtmpHeader.getMessageLength(), rtmpHeader.getMessageLength());
                inCompletePayload.put(rtmpHeader.getCsid(), buffer);
                previousHeaders.put(rtmpHeader.getCsid(), rtmpHeader);
            }

            currentPayload = inCompletePayload.get(rtmpHeader.getCsid());
            if (currentPayload == null) {
                // when fmt=3 and previous body completely read, the previous msgLength play the
                // role of length
                RtmpHeader previousHeader = previousHeaders.get(rtmpHeader.getCsid());
                log.debug("current payload null,previous header:{}", previousHeader);
                currentPayload = Unpooled.buffer(previousHeader.getMessageLength(), previousHeader.getMessageLength());
                inCompletePayload.put(rtmpHeader.getCsid(), currentPayload);
                log.debug("current payload assign as :{}", currentPayload);
            }

            checkpoint(DecodeState.STATE_PAYLOAD);
        } else if (state == DecodeState.STATE_PAYLOAD) {

            final byte[] bytes = new byte[Math.min(currentPayload.writableBytes(), clientChunkSize)];
            in.readBytes(bytes);
            currentPayload.writeBytes(bytes);
            checkpoint(DecodeState.STATE_HEADER);

            if (currentPayload.isWritable()) {
                return;
            }
            inCompletePayload.remove(currentCsid);

            // then we can decode out payload
            ByteBuf payload = currentPayload;
            RtmpHeader header = previousHeaders.get(currentCsid);

            RtmpMessage msg = RtmpMessageDecoder.decode(header, payload);
            if (msg == null) {
                log.error("RtmpMessageDecoder.decode NULL");
                return;
            }

            if (msg instanceof SetChunkSize scs) {
                // we need chunksize to decode the chunk
                clientChunkSize = scs.getChunkSize();
                log.debug("------------>client set chunkSize to :{}", clientChunkSize);
            } else {
                out.add(msg);
            }
        }

    }

    private RtmpHeader readHeader(ByteBuf in) {
        RtmpHeader rtmpHeader = new RtmpHeader();

        // alway from the beginning
        int headerLength = 0;

        byte firstByte = in.readByte();
        headerLength += 1;

        // CHUNK HEADER is divided into
        // BASIC HEADER
        // MESSAGE HEADER
        // EXTENDED TIMESTAMP

        // BASIC HEADER
        // fmt and chunk steam id in first byte
        int fmt = (firstByte & 0xff) >> 6;
        int csid = (firstByte & 0x3f);

        if (csid == 0) {
            // 2 byte form
            csid = in.readByte() & 0xff + 64;
            headerLength += 1;
        } else if (csid == 1) {
            // 3 byte form
            byte secondByte = in.readByte();
            byte thirdByte = in.readByte();
            csid = ((thirdByte & 0xff) << 8) + (secondByte & 0xff) + 64;
            headerLength += 2;
        }  // that's it!


        rtmpHeader.setCsid(csid);
        rtmpHeader.setFmt(fmt);

        // basic header complete

        // MESSAGE HEADER
        switch (fmt) {
            case CHUNK_FMT_0 -> {
                int timestamp = in.readMedium();
                int messageLength = in.readMedium();
                short messageTypeId = (short) (in.readByte() & 0xff);
                int messageStreamId = in.readIntLE();
                headerLength += 11;
                if (timestamp == MAX_TIMESTAMP) {
                    long extendedTimestamp = in.readInt();
                    rtmpHeader.setExtendedTimestamp(extendedTimestamp);
                    headerLength += 4;
                }

                rtmpHeader.setTimestamp(timestamp);
                rtmpHeader.setMessageTypeId(messageTypeId);
                rtmpHeader.setMessageStreamId(messageStreamId);
                rtmpHeader.setMessageLength(messageLength);

            }
            case CHUNK_FMT_1 -> {
                int timestampDelta = in.readMedium();
                int messageLength = in.readMedium();
                short messageType = (short) (in.readByte() & 0xff);

                headerLength += 7;
                if (timestampDelta == MAX_TIMESTAMP) {
                    long extendedTimestamp = in.readInt();
                    rtmpHeader.setExtendedTimestamp(extendedTimestamp);
                    headerLength += 4;
                }

                rtmpHeader.setTimestampDelta(timestampDelta);
                rtmpHeader.setMessageLength(messageLength);
                rtmpHeader.setMessageTypeId(messageType);
            }
            case CHUNK_FMT_2 -> {
                int timestampDelta = in.readMedium();
                headerLength += 3;
                rtmpHeader.setTimestampDelta(timestampDelta);

                if (timestampDelta == MAX_TIMESTAMP) {
                    long extendedTimestamp = in.readInt();
                    rtmpHeader.setExtendedTimestamp(extendedTimestamp);
                    headerLength += 4;
                }

            }
            case CHUNK_FMT_3 -> {
                // nothing
            }
            default -> throw new RuntimeException("illegal fmt type:" + fmt);
        }

        rtmpHeader.setHeaderLength(headerLength);

        return rtmpHeader;
    }

    private void completeHeader(RtmpHeader rtmpHeader) {
        RtmpHeader prev = previousHeaders.get(rtmpHeader.getCsid());
        if (prev == null) {
            return;
        }
        switch (rtmpHeader.getFmt()) {
            case CHUNK_FMT_1 -> rtmpHeader.setMessageStreamId(prev.getMessageStreamId());

//			rtmpHeader.setTimestamp(prev.getTimestamp());
            case CHUNK_FMT_2 -> {
//			rtmpHeader.setTimestamp(prev.getTimestamp());
                rtmpHeader.setMessageLength(prev.getMessageLength());
                rtmpHeader.setMessageStreamId(prev.getMessageStreamId());
                rtmpHeader.setMessageTypeId(prev.getMessageTypeId());
            }
            case CHUNK_FMT_3 -> {
                rtmpHeader.setMessageStreamId(prev.getMessageStreamId());
                rtmpHeader.setMessageTypeId(prev.getMessageTypeId());
                rtmpHeader.setTimestamp(prev.getTimestamp());
                rtmpHeader.setTimestampDelta(prev.getTimestampDelta());
            }
            default -> {
            }
        }

    }

}
