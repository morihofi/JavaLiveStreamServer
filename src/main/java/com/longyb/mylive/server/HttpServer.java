package com.longyb.mylive.server;

import com.longyb.mylive.server.handlers.HttpRouterHandler;
import com.longyb.mylive.server.manager.StreamManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.extern.slf4j.Slf4j;

/**
@author longyubo
2020年1月7日 下午2:55:47
**/
@Slf4j
public class HttpServer implements AutoCloseable{

	private final int port;
	ChannelFuture channelFuture;

	EventLoopGroup eventLoopGroup;
	StreamManager streamManager;
	//not used currently
	int handlerThreadPoolSize;


	public HttpServer(int port, StreamManager sm, int threadPoolSize) {
		this.port = port;
		this.streamManager = sm;
		this.handlerThreadPoolSize = threadPoolSize;
	}


	public void run() throws Exception {
		eventLoopGroup = new NioEventLoopGroup();

		ServerBootstrap b = new ServerBootstrap();

		b.group(eventLoopGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpRequestDecoder());

						ch.pipeline().addLast(new HttpResponseEncoder());

						ch.pipeline().addLast(new HttpRouterHandler(streamManager));
					}
				}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

		channelFuture = b.bind(port).sync();
		log.info("HTTP server started, listening at :{}",port);

	}

	@Override
	public void close() {
		try{
			channelFuture.channel().close();
		}catch (Exception ex){
			log.error("Error closing HTTP Server", ex);
		}
	}
}
