package com.longyb.mylive.server.rtmp;

import java.security.SecureRandom;

/**
@author longyubo
2019年12月10日 下午8:49:19
**/
public class Tools {
	private static final SecureRandom random = new SecureRandom();

    public static byte[] generateRandomData(int size) {
		byte[] bytes = new byte[size];
		random.nextBytes(bytes);
		return bytes;
	}
}
