package com.longyb.mylive.server.cfg;

import lombok.Data;

/**
 * @author longyubo 2020年1月9日 下午2:29:25
 **/
@Data
public class ApplicationServerConfig {

	public static ApplicationServerConfig INSTANCE = null;

	int rtmpPort;

	boolean enableHttp;
	int httpPort;

	boolean saveFlvFile;
	String saveFlVFilePath;

	int handlerThreadPoolSize;

	FfmpegConfig ffmpeg;

}
