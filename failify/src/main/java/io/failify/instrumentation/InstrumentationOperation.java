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

package io.failify.instrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstrumentationOperation {
    private final RunSeqRuntimeOperation operation;
    private final List<String> parameters;

    private InstrumentationOperation(InstrumentationOperationBuilder builder) {
        this.operation = builder.operation;
        this.parameters = Collections.unmodifiableList(builder.parameters);
    }

    public RunSeqRuntimeOperation getOperation() {
        return operation;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public static class InstrumentationOperationBuilder {
        private InstrumentationDefinition.InstrumentationDefinitionBuilder parentBuilder;
        private final RunSeqRuntimeOperation operation;
        private List<String> parameters;


        public InstrumentationOperationBuilder(RunSeqRuntimeOperation operation, InstrumentationDefinition.InstrumentationDefinitionBuilder parentBuilder) {
            this.operation = operation;
            this.parentBuilder = parentBuilder;
            parameters = new ArrayList<>();
        }

        public InstrumentationOperationBuilder parameter(String parameter) {
            parameters.add(parameter);
            return this;
        }

        public InstrumentationOperation build() {
            return new InstrumentationOperation(this);
        }

        public InstrumentationDefinition.InstrumentationDefinitionBuilder and() {
            parentBuilder.instrumentationOperation(build());
            return parentBuilder;
        }
    }
}
