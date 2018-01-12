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

import me.arminb.spidersilk.exceptions.InstrumentationException;
import me.arminb.spidersilk.instrumentation.InstrumentationDefinition;
import me.arminb.spidersilk.instrumentation.InstrumentationOperation;
import me.arminb.spidersilk.instrumentation.Instrumentor;
import me.arminb.spidersilk.instrumentation.NodeWorkspace;
import me.arminb.spidersilk.util.ShellUtil;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JavaInstrumentor implements Instrumentor {

    @Override
    public String instrument(NodeWorkspace nodeWorkspace) throws InstrumentationException {
        List<AspectGenerator.AspectFile> aspectFiles = new ArrayList<>();
        String argFileString = "";
        for (InstrumentationDefinition instrumentationDefinition: nodeWorkspace.getInstrumentationDefinitions()) {
            AspectGenerator.AspectFile aspectFile = AspectGenerator.generate(instrumentationDefinition);
            try {
                aspectFile.save(nodeWorkspace.getWorkingDirectory());
            } catch (IOException e) {
                throw new InstrumentationException("Error in creating Java aspect files for \"" + nodeWorkspace.getApplicationAddress() + "\"!");
            }
            aspectFiles.add(aspectFile);
            argFileString += aspectFile.getAspectFileName() + "\n";
        }

        try {
            Files.write(Paths.get(nodeWorkspace.getWorkingDirectory(), "argfile"), argFileString.getBytes());
        } catch (IOException e) {
            throw new InstrumentationException("Error in creating AspectJ argfile for \"" + nodeWorkspace.getApplicationAddress() + "\"!");
        }

        try {
            String currentShell = ShellUtil.getCurrentShellAddress();
            if (currentShell == null) {
                throw new InstrumentationException("Cannot find the current system shell to run the instrumentor!");
            }

            String classPathString = "";
            if (nodeWorkspace.getLibDir() != null) {
                classPathString = " -cp " + Paths.get(nodeWorkspace.getLibDir()).toString();
            }

            Process ajcProcess = new ProcessBuilder().command(
                    currentShell, "-c" ,"ajc -inpath " + nodeWorkspace.getApplicationAddress() +
                    " @" + Paths.get(nodeWorkspace.getWorkingDirectory(), "argfile").toString() +
                    classPathString +
                    " -outjar " + Paths.get(nodeWorkspace.getWorkingDirectory(), "woven.jar").toString())
                    .redirectError(Paths.get(nodeWorkspace.getWorkingDirectory(), "aspectj.log").toFile())
                    .redirectOutput(Paths.get(nodeWorkspace.getWorkingDirectory(), "aspectj.log").toFile())
                    .start();

            ajcProcess.waitFor();

            if (ajcProcess.exitValue() != 0) {
                throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                        Paths.get(nodeWorkspace.getWorkingDirectory(), "aspectj.log").toString() + "!");
            }
        } catch (IOException e) {
            throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                    Paths.get(nodeWorkspace.getWorkingDirectory(), "aspectj.log").toString() + "!");
        } catch (InterruptedException e) {
            throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                    Paths.get(nodeWorkspace.getWorkingDirectory(), "aspectj.log").toString() + "!");
        }

        return FilenameUtils.normalize(Paths.get(nodeWorkspace.getWorkingDirectory(), "woven.jar").toString());
    }
}
