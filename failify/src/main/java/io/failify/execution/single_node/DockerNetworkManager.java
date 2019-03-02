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
 */

package io.failify.execution.single_node;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Ipam;
import com.spotify.docker.client.messages.IpamConfig;
import com.spotify.docker.client.messages.NetworkConfig;
import io.failify.Constants;
import io.failify.exceptions.RuntimeEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.util.*;

public class DockerNetworkManager {
    private final static Logger logger = LoggerFactory.getLogger(DockerNetworkManager.class);

    private final DockerClient dockerClient;
    private final String dockerNetworkId;
    private final String dockerNetworkName;
    private final String ipPrefix;
    private final String hostIp;
    private Integer currentIp;

    public DockerNetworkManager(String deploymentName, DockerClient dockerClient)
            throws RuntimeEngineException {
        this.dockerClient = dockerClient;

        dockerNetworkName = Constants.DOCKER_NETWORK_NAME_PREFIX + deploymentName + "_" + Instant.now().getEpochSecond();
        String tempNetworkId = null;
        String gateway = null, subnet = null, tempIpPrefix = null;

        // TODO is this the best way to generate subnets?
        for (int i=2; i<255; i++) {
            subnet = "10." + i + ".0.0/16";
            gateway = "10." + i + ".0.1";
            tempIpPrefix = "10." + i + ".0.";

            try {
                tempNetworkId = dockerClient.createNetwork(NetworkConfig.builder()
                        .driver("bridge")
                        .name(dockerNetworkName)
                        .ipam(Ipam.create("default", Arrays.asList(IpamConfig.create(subnet, null, gateway))))
                        .build()).id();
                logger.info("Docker network {} is created!", tempNetworkId);
                break;
            } catch (InterruptedException | DockerException e) {
                logger.debug("Creating docker network with subnet {} and gateway {} failed", subnet, gateway);
            }
        }

        if (tempNetworkId == null) {
            throw new RuntimeEngineException("Error in creating docker network!");
        }

        dockerNetworkId = tempNetworkId;
        hostIp = gateway;
        ipPrefix = tempIpPrefix;
        currentIp = 2;
        logger.info("Gateway is {}", gateway);
        logger.info("Subnet is {}", subnet);
    }

    public String dockerNetworkId() {
        return dockerNetworkId;
    }

    public String dockerNetworkName() {
        return dockerNetworkName;
    }

    public void deleteDockerNetwork() throws RuntimeEngineException {
        try {
            if (dockerNetworkId != null) {
                dockerClient.removeNetwork(dockerNetworkId);
            }
        } catch (InterruptedException | DockerException e) {
            throw new RuntimeEngineException("Error in deleting docker network" + dockerNetworkId + "!", e);
        }
    }

    public String getHostIpAddress() {
        return hostIp;
    }

    public String getClientContainerIpAddress() throws RuntimeEngineException {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface networkInterface = en.nextElement();
                for (Enumeration<InetAddress> enIp = networkInterface.getInetAddresses(); enIp.hasMoreElements();) {
                    InetAddress inetAddress = enIp.nextElement();
                    if (inetAddress.getHostAddress().startsWith(ipPrefix)) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
            return null;
        } catch (SocketException e) {
            throw new RuntimeEngineException("Error while getting the list of the inerfaces in the system", e);
        }
    }

    public String getNewIpAddress() {
        return ipPrefix + currentIp++;
    }
}
