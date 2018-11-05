/*
 * MIT License
 *
 * Copyright (c) 2017-2018 Armin Balalaie
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
 */

package me.arminb.spidersilk.dsl.entities;

public class ExposedPortDefinition {

    private final PortType type;
    private final Integer port;

    public ExposedPortDefinition(Integer port, PortType type) {
        this.type = type;
        this.port = port;
    }

    public Integer port() {
        return port;
    }

    public PortType type() {
        return type;
    }

    @Override
    public String toString() {
        return port + "/" + type.name().toLowerCase();
    }

    public static ExposedPortDefinition fromString(String portDef) {
        String[] parts = portDef.split("/");
        if (parts.length != 2) {
            return null;
        }

        return new ExposedPortDefinition(Integer.parseInt(parts[0]), PortType.valueOf(parts[1].toUpperCase()));
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!ExposedPortDefinition.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final ExposedPortDefinition other = (ExposedPortDefinition) obj;
        if ((this.port == null) ? (other.port != null) : !this.port.equals(other.port)) {
            return false;
        }
        if ((this.type == null) ? (other.type != null) : !this.type.equals(other.type)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return type.hashCode() * port.hashCode();
    }
}
