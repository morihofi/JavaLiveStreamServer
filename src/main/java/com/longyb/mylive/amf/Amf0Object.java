package com.longyb.mylive.amf;

import java.io.Serial;
import java.util.LinkedHashMap;

public class Amf0Object extends LinkedHashMap<String, Object> {

    @Serial
    private static final long serialVersionUID = 1L;

    public Amf0Object addProperty(String key, Object value) {
        put(key, value);
        return this;
    }
}
