package com.longyb.mylive.server.rtmp.messages;

import java.util.Arrays;

import com.longyb.mylive.server.rtmp.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AudioMessage extends RtmpMediaMessage {
	byte[] audioData;

	@Override
	public int getOutboundCsid() {
		return 10;
	}

	@Override
	public ByteBuf encodePayload() {
		return Unpooled.wrappedBuffer(audioData);
	}

	@Override
	public int getMsgType() {
		return Constants.MSG_TYPE_AUDIO_MESSAGE;
	}

	@Override
	public byte[] raw() {

		return audioData;
	}

	public boolean isAACAudioSpecificConfig(){
		return audioData.length>1 && audioData[1]==0;
	}

	@Override
	public String toString() {
		return "AudioMessage [audioData=" + Arrays.toString(audioData) + ", timestampDelta=" + timestampDelta
				+ ", timestamp=" + timestamp + ", inboundHeaderLength=" + inboundHeaderLength + ", inboundBodyLength="
				+ inboundBodyLength + "]";
	}


}
