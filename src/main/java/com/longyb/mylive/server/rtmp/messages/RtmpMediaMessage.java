package com.longyb.mylive.server.rtmp.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class RtmpMediaMessage  extends RtmpMessage{
	Integer timestampDelta;
	Integer timestamp;
	
	public abstract byte[] raw() ;
}
