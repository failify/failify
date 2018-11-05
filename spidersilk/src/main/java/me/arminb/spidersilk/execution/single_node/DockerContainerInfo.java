package me.arminb.spidersilk.execution.single_node;

import me.arminb.spidersilk.dsl.entities.ExposedPortDefinition;
import java.util.Map;

public class DockerContainerInfo {
    private final String containerId;
    private final String ipAddress;
    private Map<ExposedPortDefinition, Integer> portMapping;

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

    public void setPortMapping(Map<ExposedPortDefinition, Integer> portMapping) {
        this.portMapping = portMapping;
    }

    public Integer getPortMapping(ExposedPortDefinition portDefinition) {
        return portMapping.get(portDefinition);
    }
}
