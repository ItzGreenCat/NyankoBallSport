package dev.onyxcat.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.onyxcat.network.EnvelopeCrypto;
import dev.onyxcat.network.HeaderIdentity;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class SessionCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    public static void saveSession(String fileName) throws IOException {
        SessionWrapper wrapper = new SessionWrapper();
        MAPPER.writeValue(new File(fileName + ".yaml"),wrapper);
    }
    public static void loadSession(String fileName) throws IOException {
        MAPPER.readValue(new File(fileName + ".yaml"),SessionWrapper.class).unwarp();
    }
    public static void removeSession(String fileName) {
        File file = new File(fileName + ".yaml");
        if(file.exists()){
            file.delete();
        }
    }
}
