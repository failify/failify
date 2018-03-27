/*
 * MIT License
 *
 * Copyright (c) 2017 Armin Balalaie
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
 *
 */

package me.arminb.spidersilk.verification;

import me.arminb.spidersilk.dsl.entities.Deployment;
import me.arminb.spidersilk.dsl.events.internal.BlockingEvent;
import me.arminb.spidersilk.dsl.events.internal.SchedulingEvent;
import me.arminb.spidersilk.exceptions.DeploymentEntityNotFound;
import me.arminb.spidersilk.exceptions.DeploymentVerificationException;
import me.arminb.spidersilk.exceptions.RunSequenceVerificationException;

import java.util.*;

/**
 * verifies the syntax of the run sequence and will set the dependency of each event in it.
 */
public class RunSequenceVerifier extends DeploymentVerifier {
    private List<Stack<String>> levelStack = new ArrayList<>();
    private List<List<String>> levelHistory= new ArrayList<>();
    private Map<String, String> stackTraceToLastBlockingEvent = new HashMap<>();
    private String readMode; // b: before expression, e: reading identifier, ap: after closing parenthesis, ao: after operators
    private int parenthesisDepth;
    private char currentChar;
    private int currentIndex;

    public RunSequenceVerifier(Deployment deployment) {
        super(deployment);
    }


    public void verify() {
        checkUniquenessOfEventNames();

        String runSequence = deployment.getRunSequence().replaceAll("\\s+", "");
        levelStack = new ArrayList<>();
        levelHistory= new ArrayList<>();
        levelStack.add(new Stack<>());
        levelHistory.add(new ArrayList<>());
        readMode = "b";
        parenthesisDepth = 0;
        String tempId = "";

        for (currentIndex=0; currentIndex<runSequence.length(); currentIndex++) {
            currentChar = runSequence.charAt(currentIndex);
            if (readMode.equals("b")) {
                if (currentChar == '(') {
                    prepareOpenParenthesis();
                } else if (Character.isLetterOrDigit(currentChar)) {
                    tempId = String.valueOf(currentChar);
                    readMode = "e";
                } else {
                    throw new RunSequenceVerificationException(currentIndex, "Expecting ( or alphanum!");
                }
            } else if (readMode.equals("e")) {
                if (Character.isLetterOrDigit(currentChar)) {
                    tempId += currentChar;
                }
                else {
                    createNewIdAndSetDependency(tempId);
                    if (currentChar == ')') {
                        prepareCloseParenthesis();
                        readMode = "ap";
                    }
                    if (currentChar == '*' || currentChar == '|') {
                        levelStack.get(parenthesisDepth).push(String.valueOf(currentChar));
                        readMode = "ao";
                    }
                }
            } else if (readMode.equals("ao")) {
                if (currentChar == '(') {
                    prepareOpenParenthesis();
                    readMode = "b";
                } else if (Character.isLetterOrDigit(currentChar)) {
                    tempId = String.valueOf(currentChar);
                    readMode = "e";
                } else {
                    throw new RunSequenceVerificationException(currentIndex, "Expecting ( or alphanum!");
                }
            } else if (readMode.equals("ap")) {
                if (currentChar == ')') {
                    prepareCloseParenthesis();
                }
                if (currentChar == '*' || currentChar == '|') {
                    levelStack.get(parenthesisDepth).push(String.valueOf(currentChar));
                    readMode = "ao";
                }
            }
        }

        if (!readMode.equals("e") && !readMode.equals("ap")) {
            throw new RunSequenceVerificationException(currentIndex, "Incomplete expression!");
        }

        if (readMode.equals("e")) {
            createNewIdAndSetDependency(tempId);
        }

        if (parenthesisDepth != 0) {
            throw new RunSequenceVerificationException(currentIndex, "Unequal number of opened and closed parenthesis!");
        }
    }

    private void checkUniquenessOfEventNames() {
        HashMap<String, Boolean> occurrenceMap = new HashMap<>();

        for (String id: deployment.getRunSequence().split("\\W+")) {
            if (occurrenceMap.containsKey(id)) {
                throw new DeploymentVerificationException("Run sequence cannot contain multiple uses of the same event (" + id + ")!");
            }
            occurrenceMap.put(id, true);
        }
    }

    private void createNewIdAndSetDependency(String id) {
        resolveDependency(id);
        resolveBlockingCondition(id);
        levelHistory.get(parenthesisDepth).add(id);
        levelStack.get(parenthesisDepth).push(id);
    }

    private void prepareOpenParenthesis() {
        parenthesisDepth++;
        levelStack.add(parenthesisDepth, new Stack<>());
        levelHistory.add(parenthesisDepth, new ArrayList<>());
    }

    private void prepareCloseParenthesis() {
        if (parenthesisDepth>0) {
            String commaSeparatedLevelIds = levelHistory.get(parenthesisDepth).toString().replaceAll("[\\s\\[\\]]", "");
            levelStack.remove(parenthesisDepth);
            levelHistory.remove(parenthesisDepth);
            parenthesisDepth--;
            try {
                // pop the last two items in the stack since they were there for the use of events inside the parenthesis
                levelStack.get(parenthesisDepth).pop();
                levelStack.get(parenthesisDepth).pop();
            } catch (EmptyStackException e) {}
            levelStack.get(parenthesisDepth).push(commaSeparatedLevelIds);
            levelHistory.get(parenthesisDepth).add(commaSeparatedLevelIds);
        } else {
            throw new RunSequenceVerificationException(currentIndex, "Cannot close a parenthesis before opening it!");
        }
    }

    private void resolveDependency(String eventName) {
        String operator = null;
        String prevOperand = null;
        int currentDepth;

        // check if event name exists
        if (deployment.getReferableDeploymentEntity(eventName) == null) {
            throw new DeploymentEntityNotFound(eventName);
        }

        // go down in levelStack to find the last operator and operand
        for (currentDepth = parenthesisDepth; currentDepth>=0; currentDepth--) {
            if (levelStack.get(currentDepth).empty())
                continue;
            operator = levelStack.get(currentDepth).pop();
            prevOperand = levelStack.get(currentDepth).pop();
            break;
        }

        if (prevOperand == null) {
            deployment.getReferableDeploymentEntity(eventName).setDependsOn(null);
        }
        // check if the previous operand was an expression inside parenthesis or not
        else if (prevOperand.contains(",")) {
            // It is
            String dependency = null;
            if (operator.equals("*")) {
                deployment.getReferableDeploymentEntity(eventName).setDependsOn(prevOperand);
            } else if (operator.equals("|")) {
                dependency = deployment.getReferableDeploymentEntity(prevOperand.split(",")[0]).getDependsOn();
                deployment.getReferableDeploymentEntity(eventName).setDependsOn(dependency);
            }
        } else {
            if (operator.equals("|")) {
                deployment.getReferableDeploymentEntity(eventName).setDependsOn(
                        deployment.getReferableDeploymentEntity(prevOperand).getDependsOn());
            } else if (operator.equals("*")) {
                deployment.getReferableDeploymentEntity(eventName).setDependsOn(prevOperand);
            }
        }

        // push back the operand and the operator to the corresponding stack if it is not in the current depth since they
        // should be popped when the parenthesis is closed
        if (currentDepth!=parenthesisDepth && operator!=null && prevOperand!=null) {
            levelStack.get(currentDepth).push(prevOperand);
            levelStack.get(currentDepth).push(operator);
        }
    }

    private void resolveBlockingCondition(String eventName) {
        BlockingEvent blockingEvent = deployment.getBlockingEvent(eventName);

        // check if event is a blocking event
        if (blockingEvent != null) {
            // set blocking condition to the last seen blocking event for the event stack trace
            String stackTraceMapKey = blockingEvent.getSchedulingPoint() + "-" + blockingEvent.getStack(deployment);
            blockingEvent.setBlockingCondition(stackTraceToLastBlockingEvent.get(stackTraceMapKey));
            // update the the last seen blocking event for the event stack trace
            // TODO a stack trace can be a subset of another stack trace
            stackTraceToLastBlockingEvent.put(stackTraceMapKey, eventName);
        }
    }
}
