package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.SpiderSilkRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NextEventReceiptTimeoutCheckerThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(NextEventReceiptTimeoutCheckerThread.class);
    SpiderSilkRunner spiderSilkRunner;

    public NextEventReceiptTimeoutCheckerThread(SpiderSilkRunner spiderSilkRunner) {
        super("timeoutCheckerThread");
        this.spiderSilkRunner = spiderSilkRunner;
    }

    @Override
    public void run() {
        while (!spiderSilkRunner.isStopped()) {
            try {
                sleep(100);
                if (EventService.getInstance().isLastEventReceivedTimeoutPassed()) {
                    logger.error("The timeout for receiving the next event is passed!");
                    spiderSilkRunner.stop();
                }
            } catch (InterruptedException e) {
                logger.error("Timeout checker thread has been interrupted!");
            }
        }
    }
}
