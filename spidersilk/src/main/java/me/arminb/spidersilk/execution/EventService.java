package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class EventService {
    private static Logger logger = LoggerFactory.getLogger(EventService.class);
    private static EventService instance;
    private ConcurrentHashMap<String, Boolean> eventCheckList;
    private final Deployment deployment;

    public static EventService initialize(Deployment deployment) {
        instance = new EventService(deployment);
        return instance;
    }

    public static EventService getInstance() {
        if (instance == null) {
            throw new RuntimeException("You should first initialize the instance");
        }
        return instance;
    }

    private EventService(Deployment deployment) {
        this.deployment = deployment;
        eventCheckList = new ConcurrentHashMap<>();
    }

    public void receiveEvent(String eventName) {
        eventCheckList.put(eventName, true);
        logger.info("Event " + eventName + " received!");
    }

    public boolean areDependenciesMet(String eventName) {
        ReferableDeploymentEntity deploymentEntity = deployment.getReferableDeploymentEntity(eventName);
        if (deploymentEntity == null) {
            return false;
        }

        if (deploymentEntity.getDependsOn() == null) {
            return true;
        }

        String[] dependencies = deploymentEntity.getDependsOn().split(",");
        for (String dependency: dependencies) {
            synchronized (eventCheckList) {
                if (!eventCheckList.containsKey(dependency)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isTheRunSequenceCompleted() {
        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (!eventCheckList.containsKey(id)) {
                return false;
            }
        }
        return true;
    }
}
