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
import me.arminb.spidersilk.instrumentation.Instrumentor;
import me.arminb.spidersilk.util.JarUtil;
import me.arminb.spidersilk.workspace.NodeWorkspace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class JavaInstrumentor implements Instrumentor {

    private static final Logger logger = LoggerFactory.getLogger(JavaInstrumentor.class);

    @Override
    public String instrument(NodeWorkspace nodeWorkspace, List<InstrumentationDefinition> instrumentationDefinitions)
            throws InstrumentationException {
        List<AspectGenerator.AspectFile> aspectFiles = new ArrayList<>();
        String argFileString = "";
        for (InstrumentationDefinition instrumentationDefinition: instrumentationDefinitions) {
            AspectGenerator.AspectFile aspectFile = AspectGenerator.generate(instrumentationDefinition);
            try {
                aspectFile.save(nodeWorkspace.getRootDirectory());
            } catch (IOException e) {
                throw new InstrumentationException("Error in creating Java aspect files for \"" + nodeWorkspace.getInstrumentableAddress() + "\"!");
            }
            aspectFiles.add(aspectFile);
            argFileString += aspectFile.getAspectFileName() + "\n";
        }

        try {
            Files.write(Paths.get(nodeWorkspace.getRootDirectory(), "argfile"), argFileString.getBytes());
        } catch (IOException e) {
            throw new InstrumentationException("Error in creating AspectJ argfile for \"" + nodeWorkspace.getInstrumentableAddress() + "\"!");
        }

        try {
            String classPathString = "";
            if (nodeWorkspace.getLibraryPaths() != null) {
                classPathString = "\"" + nodeWorkspace.getLibraryPaths() + "\"";
            }

            // TODO this is not a cross-platform way of doing this and only works on unix and linux based systems
            Process ajcProcess = new ProcessBuilder().command(
                    "ajc", "-inpath", nodeWorkspace.getInstrumentableAddress(),
                    "@" + Paths.get(nodeWorkspace.getRootDirectory(), "argfile").toString(),
                    "-cp", classPathString,
                    "-outjar", Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar").toString())
                    .redirectError(Paths.get(nodeWorkspace.getRootDirectory(), "aspectj.log").toFile())
                    .redirectOutput(Paths.get(nodeWorkspace.getRootDirectory(), "aspectj.log").toFile())
                    .start();

            ajcProcess.waitFor();

            if (ajcProcess.exitValue() != 0) {
                throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                        Paths.get(nodeWorkspace.getRootDirectory(), "aspectj.log").toString() + "!");
            }
        } catch (IOException e) {
            throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                    Paths.get(nodeWorkspace.getRootDirectory(), "aspectj.log").toString() + "!");
        } catch (InterruptedException e) {
            throw new InstrumentationException("Error in instrumenting using AspectJ. See log file in " +
                    Paths.get(nodeWorkspace.getRootDirectory(), "aspectj.log").toString() + "!");
        }

        try {
            if (new File(nodeWorkspace.getInstrumentableAddress()).isDirectory()) {
                JarFile jarFile = new JarFile(Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar")
                        .toAbsolutePath().toString());
                JarUtil.unzipJar(Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar")
                        .toAbsolutePath().toString(), nodeWorkspace.getInstrumentableAddress());
            } else {
                FileUtils.copyFile(Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar").toFile(),
                        new File(nodeWorkspace.getInstrumentableAddress()));
            }
            Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar").toFile().delete();

        } catch (IOException e) {
            throw new InstrumentationException("Error while trying to unzip aspectj jar output!");
        }

        return FilenameUtils.normalize(Paths.get(nodeWorkspace.getRootDirectory(), "woven.jar").toString());
    }
}
