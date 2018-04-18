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

package me.arminb.spidersilk.dsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * All of the entities in the deployment definition should extend this class
 */
public abstract class DeploymentEntity {
    protected final String name;

    protected DeploymentEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * This is the base builder class for all of the entities inside the deployment definition which helps to implement
     * a hierarchical builder system
     * @param <T> The class that is going to be build
     * @param <S> The parent builder class
     */
    public abstract static class DeploymentBuilderBase<T extends DeploymentEntity, S extends DeploymentBuilderBase> {
        protected final String name;
        protected S parentBuilder;

        public DeploymentBuilderBase(S parentBuilder, String name) {
            this.name = name;
            this.parentBuilder = parentBuilder;
        }

        public DeploymentBuilderBase(S parentBuilder, DeploymentEntity instance) {
            this.name = new String(instance.getName());
            this.parentBuilder = parentBuilder;
        }

        public String getName() {
            return name;
        }

        public S and() {
            if (parentBuilder == null) {
                throw new RuntimeException("and() cannot be called on a builder without parent!");
            }
            returnToParent(build());
            return parentBuilder;

        }

        public abstract T build();

        protected abstract void returnToParent(T builtObj);
    }
}
