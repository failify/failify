package me.arminb.spidersilk.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class HostUtil {
    public static String getLocalIpAddress() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }
}
