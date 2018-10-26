package me.arminb.spidersilk.execution.single_node;

public class DockerContainerInfo {
    private final String containerId;
    private final String ipAddress;

    public DockerContainerInfo(String containerId, String ipAddress) {
        this.containerId = containerId;
        this.ipAddress = ipAddress;
    }

    public String containerId() {
        return containerId;
    }

    public String ip() {
        return ipAddress;
    }
}
