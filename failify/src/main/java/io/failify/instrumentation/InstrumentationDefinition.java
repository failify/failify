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

import io.failify.instrumentation.runseq.RunSeqRuntimeOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstrumentationDefinition {
    private final InstrumentationPoint instrumentationPoint;
    private final List<InstrumentationOperation> instrumentationOperations;

    public static InstrumentationDefinitionBuilder builder() {
        return new InstrumentationDefinitionBuilder();
    }

    private InstrumentationDefinition(InstrumentationDefinitionBuilder builder) {
        this.instrumentationPoint = builder.instrumentationPoint;
        this.instrumentationOperations = Collections.unmodifiableList(builder.instrumentationOperations);
    }

    public InstrumentationPoint getInstrumentationPoint() {
        return instrumentationPoint;
    }

    public List<InstrumentationOperation> getInstrumentationOperations() {
        return instrumentationOperations;
    }

    public static class InstrumentationDefinitionBuilder {
        private InstrumentationPoint instrumentationPoint;
        private List<InstrumentationOperation> instrumentationOperations;

        public InstrumentationDefinitionBuilder() {
            this.instrumentationOperations = new ArrayList<>();
        }

        public InstrumentationDefinitionBuilder instrumentationPoint(String methodName, InstrumentationPoint.Position position) {
            this.instrumentationPoint = new InstrumentationPoint(methodName, position);
            return this;
        }

        public InstrumentationDefinitionBuilder instrumentationOperation(InstrumentationOperation instrumentationOperation) {
            this.instrumentationOperations.add(instrumentationOperation);
            return this;
        }

        public InstrumentationOperation.InstrumentationOperationBuilder withInstrumentationOperation(
                RunSeqRuntimeOperation operation) {
            return new InstrumentationOperation.InstrumentationOperationBuilder(operation, this);
        }

        public InstrumentationDefinition build() {
            return new InstrumentationDefinition(this);
        }
    }
}
