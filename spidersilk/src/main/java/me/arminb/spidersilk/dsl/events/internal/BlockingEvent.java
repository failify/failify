package me.arminb.spidersilk.dsl.events.internal;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.InternalEvent;

public abstract class BlockingEvent extends InternalEvent {
    protected String blockingCondition;

    protected BlockingEvent(String name, String nodeName) {
        super(name, nodeName);
    }

    public String getBlockingCondition() {
        return blockingCondition;
    }

    public void setBlockingCondition(String blockingCondition) {
        this.blockingCondition = this.blockingCondition == null ? blockingCondition : throwBlockingConditionIsSet_();
    }

    private String throwBlockingConditionIsSet_() {
        throw new RuntimeException("blockingCondition is already set");
    }

    public boolean isBlocking() {
        return true;
    }

    public abstract String getStack(Deployment deployment);

    public abstract SchedulingPoint getSchedulingPoint();

}
