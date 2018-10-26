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

package me.arminb.spidersilk.rt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StackMatcher {
    public boolean match(String stack) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String[] inputTraces = stack.trim().split(",");
        List<String> inputList = Arrays.asList(inputTraces);
        Collections.reverse(inputList);
        inputTraces = inputList.toArray(new String[inputList.size()]);
        for (int i=0; i<inputTraces.length; i++) {
            if (!getFullTraceString(elements[i+4]).equals(inputTraces[i].trim())) {
                return false;
            }
        }
        return true;
    }

    private String getFullTraceString(StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName();
    }
}
