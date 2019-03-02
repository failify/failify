package io.failify.util;

import java.io.IOException;
import java.io.PipedInputStream;

public class StreamUtil {
    public static String pipedInputStreamToString(PipedInputStream stream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            byte[] buffer = new byte[stream.available()];
            if (stream.read(buffer) == 0) {
                break;
            }
            stringBuilder.append(new String(buffer));
        }

        return stringBuilder.toString();
    }
}
