package me.arminb.spidersilk.exceptions;

public class WorkspaceException extends Exception {
    public WorkspaceException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public WorkspaceException(String msg) {
        super(msg);
    }
}
