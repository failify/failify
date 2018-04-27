package me.arminb.spidersilk;

public class Constants {
    // DSL
    public static final String DEFAULT_APP_HOME_ENVVAR_NAME = "SPIDERSILK_APPLICATION_HOME";
    public static final String INSTRUMENTATION_POINT_MAIN = "main";
    public static final String DEFAULT_BASE_DOCKER_IMAGE_NAME = "ubuntu";

    // Execution Engine
    public final static String DEFAULT_WORKING_DIRECTORY_NAME = ".SpiderSilkWorkingDirectory";
    public final static String NODE_ROOT_DIRECTORY_NAME = "root";
    public final static String NODE_LOG_DIRECTORY_NAME = "logs";
    public final static String DOCKER_NETWORK_NAME_PREFIX = "spidersilk_";
    public final static String DOCKER_CONTAINER_NAME_PREFIX = "spidersilk_";
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_RESTART = 5;
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP = 5;

}
