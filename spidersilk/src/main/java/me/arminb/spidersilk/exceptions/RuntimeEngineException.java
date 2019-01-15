package me.arminb.spidersilk.exceptions;

public class RuntimeEngineException extends Exception {
    public RuntimeEngineException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RuntimeEngineException(String msg) {
        super(msg);
    }
}
