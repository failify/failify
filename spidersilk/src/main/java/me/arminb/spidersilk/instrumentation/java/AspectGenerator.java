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

package me.arminb.spidersilk.instrumentation.java;

import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.instrumentation.*;
import me.arminb.spidersilk.util.HashingUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AspectGenerator {
    private static String aspectTemplate;
    private static Logger logger = LoggerFactory.getLogger(AspectGenerator.class);
    private static Map<SpiderSilkRuntimeOperation, String> operationToStringMap;

    static {
        operationToStringMap = new HashMap<>();
        operationToStringMap.put(SpiderSilkRuntimeOperation.ALLOW_BLOCKING, "me.arminb.spidersilk.rt.SpiderSilk.getInstance().allowBlocking");
        operationToStringMap.put(SpiderSilkRuntimeOperation.ENFORCE_ORDER, "me.arminb.spidersilk.rt.SpiderSilk.getInstance().enforceOrder");
        operationToStringMap.put(SpiderSilkRuntimeOperation.GARBAGE_COLLECTION, "me.arminb.spidersilk.rt.SpiderSilk.getInstance().garbageCollection");

        try {
            aspectTemplate = IOUtils.toString(AspectGenerator.class.getClassLoader().getResourceAsStream("AspectTemplate.java"));
        } catch (IOException e) {
            logger.error("Error reading AspectJ template file!", e);
        }
    }

    private static String getAspectName(InstrumentationDefinition instrumentationDefinition) {
        return "ASPECT_" + HashingUtil.md5(instrumentationDefinition.getInstrumentationPoint().getMethodName() +
                instrumentationDefinition.getInstrumentationPoint().getPosition().toString() +
                new Date().getTime()
        );
    }

    private static String createInstructionString(InstrumentationOperation operation) {
        if (operationToStringMap.containsKey(operation.getOperation())) {
            String retString = operationToStringMap.get(operation.getOperation()) + "(";
            for (String param: operation.getParameters()) {
                retString += "\"" + param + "\", ";
            }
            if (retString.endsWith(", ")) {
                retString = retString.substring(0, retString.length() - 2);
            }
            retString += ");";
            return retString;
        } else {
            return "";
        }
    }

    public static AspectFile generate(InstrumentationDefinition instrumentationDefinition) {
        String aspectName = getAspectName(instrumentationDefinition);
        String beforeInstructions = "";
        String afterInstructions = "";
        String methodName;

        if (instrumentationDefinition.getInstrumentationPoint().getMethodName().equals(Constants.INSTRUMENTATION_POINT_MAIN)) {
            methodName = "public static void main(String[])";
        } else {
            methodName = "* " + instrumentationDefinition.getInstrumentationPoint().getMethodName() + "(..)";
        }

        for (InstrumentationOperation operation: instrumentationDefinition.getInstrumentationOperations()) {
            if (instrumentationDefinition.getInstrumentationPoint().getPosition() == InstrumentationPoint.Position.BEFORE) {
                beforeInstructions += createInstructionString(operation) + "\n";
            } else if (instrumentationDefinition.getInstrumentationPoint().getPosition() == InstrumentationPoint.Position.AFTER) {
                afterInstructions += createInstructionString(operation) + "\n";
            }
        }

        String aspectBody = aspectTemplate.replace("{{aspect_name}}", aspectName)
            .replace("{{instrumentation_point}}", methodName)
            .replace("{{before_instructions}}", beforeInstructions)
            .replace("{{after_instructions}}", afterInstructions);

        return new AspectFile(aspectName + ".java", aspectBody);
    }

    public static class AspectFile {
        private final String aspectFileName;
        private final String aspectBody;

        public AspectFile(String aspectFileName, String aspectBody) {
            this.aspectFileName = aspectFileName;
            this.aspectBody = aspectBody;
        }

        public String getAspectFileName() {
            return aspectFileName;
        }

        public String getAspectBody() {
            return aspectBody;
        }

        public void save(String directory) throws IOException {
            Files.write(Paths.get(directory, aspectFileName), aspectBody.getBytes());
        }
    }
}
