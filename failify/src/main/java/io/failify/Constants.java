/*
 * MIT License
 *
 * Copyright (c) 2017-2019 Armin Balalaie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.failify;

public class Constants {
    // DSL
    public static final String JVM_CLASSPATH_ENVVAR_NAME = "FAILIFY_JVM_CLASSPATH";
    public static final String INSTRUMENTATION_POINT_MAIN = "main";
    public static final String DEFAULT_BASE_DOCKER_IMAGE_NAME = "ubuntu";

    // Execution Engine
    public final static String DEFAULT_WORKING_DIRECTORY_NAME = ".FailifyWorkingDirectory";
    public final static String REPLACEMENT_DIRECTORY_NAME = "replacements";
    public final static String NODE_ROOT_DIRECTORY_NAME = "root";
    public final static String NODE_LOG_DIRECTORY_NAME = "logs";
    public final static String SHAERD_DIRECTORIES_ROOT_NAME = "sharedDirectories";
    public final static String FAKETIME_DIRECTORY_NAME= "fakeTime";
    public final static String FAKETIME_TARGET_BASE_PATH= "/usr/local/lib/faketime/";
    public final static String FAKETIME_CONTROLLER_FILE_NAME = "failify_libfaketime";
    public final static String FAKETIME_LIB_FILE_NAME= "libfaketime.so.1";
    public final static String FAKETIMEMT_LIB_FILE_NAME= "libfaketimeMT.so.1";
    public final static String WRAPPER_SCRIPT_NAME = "failify_wrapper_script";
    public final static String DO_INIT_FILE_NAME = "failify_do_init";
    public final static String CONSOLE_OUTERR_FILE_NAME = "failify_out_err";
    public final static String DECOMPRESSED_DIRECTORIES_ROOT_NAME = "decompressed";
    public final static String DOCKER_NETWORK_NAME_PREFIX = "failify_";
    public final static String DOCKER_CONTAINER_NAME_PREFIX = "failify_";
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_RESTART = 5;
    public final static Integer DEFAULT_SECONDS_TO_WAIT_BEFORE_FORCED_STOP = 5;
    public final static String FAILIFY_EVENT_SERVER_IP_ADDRESS_ENV_VAR = "FAILIFY_EVENT_SERVER_IP_ADDRESS";
    public final static String FAILIFY_EVENT_SERVER_PORT_NUMBER_ENV_VAR = "FAILIFY_EVENT_SERVER_PORT_NUMBER";
}
