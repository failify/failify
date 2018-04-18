package me.arminb.spidersilk.util;

import java.io.File;
import java.nio.file.Path;

public class PathUtil {
    public static String getLastFolderOrFileName(String path) {
        return new File(path).getName();
    }
}
