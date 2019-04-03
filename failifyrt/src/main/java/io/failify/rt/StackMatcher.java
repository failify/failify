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
 *
 */

package io.failify.rt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class is responsible for matching a given stack trace against the current stack trace
 */
public class StackMatcher {

    /**
     * This method matches the given stack arg against the current stack trace
     * @param stack the list of methods separated by comma where the last called method comes in the end
     * @return returns true if the given stack matches exactly the current stack, otherwise false
     */
    public boolean match(String stack) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String[] inputTraces = stack.trim().split(",");
        List<String> inputList = Arrays.asList(inputTraces);
        Collections.reverse(inputList);
        inputTraces = inputList.toArray(new String[inputList.size()]);

        int curIndexToMatch = 0;
        // i=2 is needed to get rid of getStackTrace and match method signatures
        for (int i=2; i<elements.length; i++) {
            if (getFullTraceString(elements[i]).equals(inputTraces[curIndexToMatch].trim())) {
                curIndexToMatch++;
                if (curIndexToMatch == inputTraces.length) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * transforms a stack trace element into full stack trace string in the format of package.class.method
     * @param element the stack trace element
     * @return an string in the format of package.class.method
     */
    private String getFullTraceString(StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName();
    }
}
