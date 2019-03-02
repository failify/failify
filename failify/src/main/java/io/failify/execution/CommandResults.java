package io.failify.execution;

/**
 * This class is used as the response when running a command inside a node
 */
public class CommandResults {
    private final String nodeName; // the node name the command ran in
    private final String command; // the actual command
    private final long exitCode; // the exit code for the command
    private final String stdOut; // the standard output for the command
    private final String stdErr; // the standard error for the command

    public CommandResults(String nodeName, String command, long exitCode, String stdOut, String stdErr) {
        this.nodeName = nodeName;
        this.command = command;
        this.exitCode = exitCode;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

    /**
     * @return the node name the command ran in
     */
    public String nodeName() {
        return nodeName;
    }

    /**
     * @return the actual command
     */
    public String command() {
        return command;
    }

    /**
     * @return the exit code for the command
     */
    public long exitCode() {
        return exitCode;
    }

    /**
     * @return the standard output for the command
     */
    public String stdOut() {
        return stdOut;
    }

    /**
     * @return the standard error for the command
     */
    public String stdErr() {
        return stdErr;
    }
}
