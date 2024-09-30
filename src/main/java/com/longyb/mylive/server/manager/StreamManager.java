package com.longyb.mylive.server.manager;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;

/**
 * all stream info store here, including publisher and subscriber and their live type video & audio
 * @author longyubo
 * @author morihofi
 */
public class StreamManager {
    private final ConcurrentHashMap<StreamName, Stream> streams = new ConcurrentHashMap<>();

    public void newStream(StreamName streamName, Stream s) {
        streams.put(streamName, s);
    }

    public boolean exist(StreamName streamName) {
        return streams.containsKey(streamName);
    }

    public Stream getStream(StreamName streamName) {
        return streams.get(streamName);
    }

    public void remove(StreamName streamName) {
        streams.remove(streamName);
    }

    public Map<StreamName, Stream> getAllStreams(){
        return Collections.unmodifiableMap(streams);
    }
}
