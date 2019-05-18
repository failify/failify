package io.failify.execution;

/**
 * Possible values for setting a ulimit for a node
 */
public enum ULimit {
    CORE,
    DATA,
    FSIZE,
    MEMLOCK,
    NOFILE,
    RSS,
    STACK,
    CPU,
    NPROC,
    AS,
    MAXLOGINS,
    MAXSYSLOGINS,
    PRIORITY,
    LOCKS,
    SIGPENDING,
    MSGQUEUE,
    NICE,
    RTPRIO,
    CHRROT;

    public static class Values {
        private final Long soft;
        private final Long hard;

        public Values(long soft, long hard) {
            this.soft = soft;
            this.hard = hard;
        }

        public Long soft() {
            return soft;
        }

        public Long hard() {
            return hard;
        }
    }
}
