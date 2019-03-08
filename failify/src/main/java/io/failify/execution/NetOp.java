package io.failify.execution;

import java.util.StringJoiner;

/**
 * This is the base class which is extended by all network operation configuration classes e.g. {@link Delay}
 */
public abstract class NetOp {
    /**
     * This method should be used to define a network delay configuration
     * @param delay the amount of the delay to be applied. If jitter is specified later, this amount will be used as the
     *              mean for the distribution of delays
     * @return an instance of {@link Delay.Builder} class for further configuration
     */
    public static Delay.Builder delay(int delay) {
        return new Delay.Builder(delay);
    }

    /**
     * This method should be used to define a remove network delay operation
     * @return an instance of {@link RemoveDelay.Builder} class
     */
    public static RemoveDelay.Builder removeDelay() {
        return new RemoveDelay.Builder();
    }

    /**
     * This method should be used to define a remove network delay operation
     * @return an instance of {@link RemoveLoss.Builder} class
     */
    public static RemoveLoss.Builder removeLoss() {
        return new RemoveLoss.Builder();
    }

    /**
     * This method should be used to define a network loss configuration
     * @param percentage the percentage of the packet loss to be applied.
     * @return an instance of {@link Loss.Builder} class for further configuration
     */
    public static Loss.Builder loss(int percentage) {
        return new Loss.Builder(percentage);
    }

    /**
     * @return the netem string to be added to tc command for the operation
     */
    abstract String getNetemString();

    /**
     * List of available statistical distributions for network operations, if applicable
     */
    public enum Dist {
        UNIFORM,
        NORMAL,
        PARETO,
        PARETONORMAL
    }

    /**
     * This is the base builder class for a network operation configuration builder
     * @param <S> this is the class that builder is supposed to build
     * @param <T> this is child builder class
     */
    public abstract static class BuilderBase<S extends NetOp, T extends BuilderBase> {
        protected abstract S build();
    }

    /**
     * This class contains configuration for imposing a network delay in a node
     */
    public static class Delay extends NetOp {
        private final Dist distribution;
        private final int delay;
        private final Integer jitter;

        /**
         * Constructor
         * @param builder a network delay configuration builder instance
         */
        protected Delay(Builder builder) {
            distribution = builder.distribution;
            delay = builder.delay;
            jitter = builder.jitter;
        }

        /**
         * @return the netem string to be added to tc command for the operation
         */
        @Override
        String getNetemString() {
            StringJoiner netem = new StringJoiner(" ");
            netem.add("delay " + delay + "ms");
            if (jitter != null) {
                netem.add(jitter + "ms");

                if (distribution != null && distribution != Dist.UNIFORM) {
                    netem.add("distribution");
                    netem.add(distribution.name().toLowerCase());
                }
            }

            return netem.toString();
        }

        @Override public String toString() {
            StringJoiner delayString = new StringJoiner(", ");
            delayString.add(String.valueOf(delay));
            if (jitter != null) {
                delayString.add(String.valueOf(jitter));

                if (distribution != null) {
                    delayString.add(distribution.name());
                }
            }

            return "network delay (" + delayString.toString() + ")";
        }

        /**
         * The builder class for building a network delay configuration
         */
        public static class Builder extends BuilderBase<Delay, Builder> {
            private Dist distribution;
            private final int delay;
            private Integer jitter;

            /**
             * Constructor
             * @param delay the amount of the delay to be applied. If jitter is specified later, this amount will be used
             *             as the mean for the distribution of delays
             */
            protected Builder(int delay) {
                distribution = Dist.UNIFORM;
                this.delay = delay;
            }

            /**
             * Sets the amount of jitter (packet delay variance) to be applied
             * @param amount the amount of jitter (packet delay variance) to be applied
             * @return current builder instance
             */
            public Builder jitter(int amount) {
                jitter = amount;
                return this;
            }

            /**
             * Sets the statistical distribution for the delay generation. This requires setting a jitter value. If not
             * specified, the default is UNIFORM distribution
             * @param distribution the statistical distribution for the delay generation
             * @return
             */
            public Builder distribution(Dist distribution) {
                this.distribution = distribution;
                return this;
            }

            /**
             * Builds the network delay configuration object
             * @return the build object
             */
            public Delay build() {
                return new Delay(this);
            }
        }
    }

    /**
     * This class represents a remove delay network operation
     */
    public static class RemoveDelay extends NetOp {
        /**
         * @return the netem string to be added to tc command for the operation
         */
        @Override String getNetemString() {
            return "";
        }

        @Override public String toString() {
            return "remove network delay";
        }

        /**
         * The builder class for building a remove network delay instance
         */
        public static class Builder extends BuilderBase<RemoveDelay, Builder> {

            @Override protected RemoveDelay build() {
                return new RemoveDelay();
            }
        }
    }

    /**
     * This class contains configuration for imposing a network loss in a node
     */
    public static class Loss extends NetOp {
        private final int percentage;

        /**
         * Constructor
         * @param builder a network loss configuration builder instance
         */
        protected Loss(Builder builder) {
            percentage = builder.percentage;
        }

        @Override public String toString() {
            return "network loss (" + percentage + "%)";
        }

        /**
         * @return the netem string to be added to tc command for the operation
         */
        @Override String getNetemString() {
            return "loss " + percentage;
        }

        /**
         * The builder class for building a network loss configuration
         */
        public static class Builder extends BuilderBase<Loss, Builder> {
            private final int percentage;

            /**
             * Constructor
             * @param percentage the percentage of the packet loss to be applied.
             */
            public Builder(int percentage) {
                this.percentage = percentage;
            }

            /**
             * Builds the network loss configuration object
             * @return the build object
             */
            @Override protected Loss build() {
                return new Loss(this);
            }
        }
    }

    /**
     * This class represents a remove loss network operation
     */
    public static class RemoveLoss extends NetOp {
        /**
         * @return the netem string to be added to tc command for the operation
         */
        @Override String getNetemString() {
            return "";
        }

        @Override public String toString() {
            return "remove network loss";
        }

        /**
         * The builder class for building a remove network loss instance
         */
        public static class Builder extends BuilderBase<RemoveLoss, Builder> {

            @Override protected RemoveLoss build() {
                return new RemoveLoss();
            }
        }
    }
}

