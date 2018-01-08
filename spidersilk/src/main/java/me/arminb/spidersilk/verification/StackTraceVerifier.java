package me.arminb.spidersilk.verification;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.dsl.events.internal.StackTraceEvent;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;

import java.util.HashMap;

public class StackTraceVerifier extends DeploymentVerifier {

    public StackTraceVerifier(Deployment deployment) {
        super(deployment);
    }

    @Override
    public void verify() {
        HashMap<String, String> stackTraceOccurrenceMap = new HashMap<>();

        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (deployment.getReferableDeploymentEntity(id) instanceof StackTraceEvent) {
                StackTraceEvent event = (StackTraceEvent) deployment.getReferableDeploymentEntity(id);
                // TODO this should only check for the method that needs to be instrumented i.e. last trace
                if (stackTraceOccurrenceMap.containsKey(event.getStack())) {
                    throw new DeploymentVerificationException("Multiple use of the same stack trace in the run" +
                            " sequence is not allowed (" + stackTraceOccurrenceMap.get(event.getStack()) +
                            "," + event.getName() + ")"
                    );
                }
                stackTraceOccurrenceMap.put(event.getStack(), event.getName());
            }
        }
    }
}
