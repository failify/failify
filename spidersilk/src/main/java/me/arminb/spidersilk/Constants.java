package me.arminb.spidersilk;

public class Constants {
    // DSL
    public static final String JVM_CLASSPATH_ENVVAR_NAME = "SPIDERSILK_JVM_CLASSPATH";
    public static final String INSTRUMENTATION_POINT_MAIN = "main";
    public static final String DEFAULT_BASE_DOCKER_IMAGE_NAME = "ubuntu";

    // Execution Engine
    public final static String DEFAULT_WORKING_DIRECTORY_NAME = ".SpiderSilkWorkingDirectory";
    public final static String NODE_ROOT_DIRECTORY_NAME = "root";
    public final static String NODE_LOG_DIRECTORY_NAME = "logs";
    public final static String SHAERD_DIRECTORIES_ROOT_NAME = "sharedDirectories";
    public final static String FAKETIME_DIRECTORY_NAME= "fakeTime";
    public final static String FAKETIME_TARGET_BASE_PATH= "/usr/local/lib/faketime/";
    public final static String FAKETIME_CONTROLLER_FILE_NAME = "spidersilk_libfaketime";
    public final static String FAKETIME_LIB_FILE_NAME= "libfaketime.so.1";
    public final static String FAKETIMEMT_LIB_FILE_NAME= "libfaketimeMT.so.1";
    public final static String WRAPPER_SCRIPT_NAME = "spidersilk_wrapper_script";
    public final static String DO_INIT_FILE_NAME = "spidersilk_do_init";
    public final static String CONSOLE_OUTERR_FILE_NAME = "spidersilk_out_err";
    public final static String DECOMPRESSED_DIRECTORIES_ROOT_NAME = "decompressed";
    public final static String DOCKER_NETWORK_NAME_PREFIX = "spidersilk_";
    public final static String DOCKER_CONTAINER_NAME_PREFIX = "spidersilk_";
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_RESTART = 5;
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP = 5;
    public final static String SPIDERSILK_EVENT_SERVER_IP_ADDRESS_ENV_VAR = "SPIDERSILK_EVENT_SERVER_IP_ADDRESS";
    public final static String SPIDERSILK_EVENT_SERVER_PORT_NUMBER_ENV_VAR = "SPIDERSILK_EVENT_SERVER_PORT_NUMBER";
}
