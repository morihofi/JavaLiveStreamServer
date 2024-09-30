package com.longyb.mylive.server.rtmp.messages;

import com.longyb.mylive.server.rtmp.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoMessage extends RtmpMediaMessage {
	byte[] videoData;

	@Override
	public ByteBuf encodePayload() {

		return Unpooled.wrappedBuffer(videoData);
	}

	@Override
	public int getOutboundCsid() {

		return 12;
	}

	@Override
	public int getMsgType() {
		return Constants.MSG_TYPE_VIDEO_MESSAGE;
	}

	public boolean isH264KeyFrame() {
		return videoData.length > 1 && videoData[0] == 0x17;
	}

	public boolean isAVCDecoderConfigurationRecord() {
		return isH264KeyFrame() && videoData.length > 2 && videoData[1] == 0x00;
	}

	@Override
	public byte[] raw() {
		return videoData;
	}

	@Override
	public String toString() {
		return "VideoMessage [timestampDelta=" + timestampDelta + ", timestamp=" + timestamp + ", inboundHeaderLength="
				+ inboundHeaderLength + ", inboundBodyLength=" + inboundBodyLength + "]";
	}



}
