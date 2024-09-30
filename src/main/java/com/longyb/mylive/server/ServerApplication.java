package com.longyb.mylive.server;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.longyb.mylive.server.cfg.ApplicationServerConfig;
import com.longyb.mylive.server.manager.StreamManager;

import lombok.extern.slf4j.Slf4j;

/**
 * @author longyubo 2020年1月7日 下午3:02:39
 **/
@Slf4j
public class ServerApplication {
    public static void main(String[] args) throws Exception {

        readConfig();
        StreamManager streamManager = new StreamManager();

        int rtmpPort = ApplicationServerConfig.INSTANCE.getRtmpPort();
        int handlerThreadPoolSize = ApplicationServerConfig.INSTANCE.getHandlerThreadPoolSize();

        RtmpServer rtmpServer = new RtmpServer(rtmpPort, streamManager, handlerThreadPoolSize);
        rtmpServer.run();

        if (!ApplicationServerConfig.INSTANCE.isEnableHttp()) {
            return;
        }

        int httpPort = ApplicationServerConfig.INSTANCE.getHttpPort();
        HttpServer httpServer = new HttpServer(httpPort, streamManager, handlerThreadPoolSize);
        httpServer.run();

    }

    private static void readConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {

            Path configFilePath = Paths.get("./config.yaml").toAbsolutePath();
            log.info("Loading configuration from {}", configFilePath);

            ApplicationServerConfig cfg = mapper.readValue(configFilePath.toFile(), ApplicationServerConfig.class);
            log.info("Using configuration: {}", cfg);

            ApplicationServerConfig.INSTANCE = cfg;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
