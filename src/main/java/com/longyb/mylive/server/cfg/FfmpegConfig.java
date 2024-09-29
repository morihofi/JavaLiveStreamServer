package com.longyb.mylive.server.cfg;

import lombok.Data;

@Data
public class FfmpegConfig {
    boolean transcodeMultipleQualities = false;
    String executablePath = null;
}
