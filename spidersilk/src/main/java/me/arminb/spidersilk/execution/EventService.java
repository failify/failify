package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.ReferableDeploymentEntity;
import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.InternalEvent;
import me.arminb.spidersilk.dsl.events.internal.BlockingEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class EventService {
    private static Logger logger = LoggerFactory.getLogger(EventService.class);

    private static EventService instance;
    private ConcurrentHashMap<String, Boolean> eventCheckList;
    private final Deployment deployment;
    private Instant lastTimeEventReceived;

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
        lastTimeEventReceived = Instant.now();
        markEligibleBlockingEventsAsReceived();
    }

    public boolean hasEventReceived(String eventName) {
        return eventCheckList.containsKey(eventName) ? true : false;
    }

    public void receiveEvent(String eventName) {
        if (!eventCheckList.containsKey(eventName)) {
            eventCheckList.put(eventName, true);
            logger.info("Event " + eventName + " received!");
            lastTimeEventReceived = Instant.now();
            // if the dependencies of any block scheduling event is met, then mark it as received
            markEligibleBlockingEventsAsReceived();
        }
    }

    public void markEligibleBlockingEventsAsReceived() {
        for (SchedulingEvent schedulingEvent: deployment.getBlockingSchedulingEvents().values()) {
            if (!eventCheckList.containsKey(schedulingEvent.getName()) && areDependenciesMet(schedulingEvent.getName())) {
                logger.info("Event " + schedulingEvent.getName() + " received!");
                eventCheckList.put(schedulingEvent.getName(), true);
            }
        }
    }

    public boolean areDependenciesMet(String eventName) {
        return areDependenciesMet(eventName, 0);
    }

    public boolean areDependenciesMet(String eventName, Integer eventInclusion) {
        ReferableDeploymentEntity deploymentEntity = deployment.getReferableDeploymentEntity(eventName);
        if (deploymentEntity == null) {
            return false;
        }

        if (deploymentEntity.getDependsOn() == null) {
            return true;
        }

        String[] dependencies = deploymentEntity.getDependsOn().split(",");
        for (String dependency: dependencies) {
            if (!eventCheckList.containsKey(dependency)) {
                return false;
            }
        }

        if (eventInclusion == 0) {
            return true;
        } else {
            return eventCheckList.containsKey(eventName);
        }
    }

    public boolean areBlockDependenciesMet(String eventName) {
        BlockingEvent blockingEvent = deployment.getBlockingEvent(eventName);
        if (blockingEvent == null || !blockingEvent.isBlocking()) {
            return true;
        }

        if (blockingEvent.getBlockingCondition() == null) {
            return true;
        }

        String[] blockDependencies = blockingEvent.getBlockingCondition().split(",");
        for (String blockDependency: blockDependencies) {
            if (!eventCheckList.containsKey(blockDependency)) {
                return false;
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

    public boolean isLastEventReceivedTimeoutPassed(Integer nextEventReceiptTimeout) {
        if (isTheRunSequenceCompleted()) {
            return false;
        }

        if (nextEventReceiptTimeout == null) {
            return false;
        }

        if (Duration.between(lastTimeEventReceived, Instant.now()).getSeconds() >= nextEventReceiptTimeout) {
            return true;
        }
        return false;
    }
}
