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

package me.arminb.spidersilk.instrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstrumentationDefinition {
    private final String instrumentationPoint;
    private final InstrumentationOperation instrumentationOperation;
    private final List<String> operationParameters;

    public static InstrumentationDefinitionBuilder builder() {
        return new InstrumentationDefinitionBuilder();
    }

    private InstrumentationDefinition(InstrumentationDefinitionBuilder builder) {
        this.instrumentationPoint = builder.instrumentationPoint;
        this.instrumentationOperation = builder.instrumentationOperation;
        this.operationParameters = Collections.unmodifiableList(builder.operationParameters);
    }

    public String getInstrumentationPoint() {
        return instrumentationPoint;
    }

    public InstrumentationOperation getInstrumentationOperation() {
        return instrumentationOperation;
    }

    public List<String> getOperationParameters() {
        return operationParameters;
    }

    public static class InstrumentationDefinitionBuilder {
        private String instrumentationPoint;
        private InstrumentationOperation instrumentationOperation;
        private List<String> operationParameters;

        public InstrumentationDefinitionBuilder() {
            this.operationParameters = new ArrayList<>();
        }

        public InstrumentationDefinitionBuilder instrumentationPoint(String point) {
            this.instrumentationPoint = point;
            return this;
        }

        public InstrumentationDefinitionBuilder instrumentationOperation(InstrumentationOperation operation) {
            this.instrumentationOperation = operation;
            return this;
        }

        public InstrumentationDefinitionBuilder addOperationParameter(String parameter) {
            operationParameters.add(parameter);
            return this;
        }

        public InstrumentationDefinition build() {
            return new InstrumentationDefinition(this);
        }
    }
}
