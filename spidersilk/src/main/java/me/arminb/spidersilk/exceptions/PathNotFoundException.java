package me.arminb.spidersilk.exceptions;

public class PathNotFoundException extends RuntimeException {
    String path;

    public PathNotFoundException(String path) {
        this.path = path;
    }


    @Override
    public String getMessage() {
        return "Path " + path + " does not exist!";
    }
}
