package io.failify.util;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class TarGzipUtil {
    /**
     * Untar an input file into a specified directory maintaining file permissions if the OS allows it
     * @param inputFile the input .tar file
     * @param outputDir the output directory file.
     * @throws IOException if the input file or output dir don't exists or something bad happens when untaring
     */
    public static void unTarGzip(String inputFile, String outputDir) throws IOException {
        File unGzippedFile = unGzip(inputFile, outputDir);

        final InputStream is = new FileInputStream(unGzippedFile);
        TarArchiveInputStream tarInputStream = null;
        try {
            tarInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
        } catch (ArchiveException e) {
            // never happens
        }
        TarArchiveEntry tarEntry = null;
        while ((tarEntry = (TarArchiveEntry)tarInputStream.getNextEntry()) != null) {
            final File outputFile = new File(outputDir, tarEntry.getName());
            if (tarEntry.isDirectory()) {
                if (!outputFile.exists()) {
                    if (!outputFile.mkdirs()) {
                        throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
                    }
                }
            } else {
                final OutputStream outputFileStream = new FileOutputStream(outputFile);
                IOUtils.copy(tarInputStream, outputFileStream);
                setPermissions(outputFile, tarEntry.getMode());
                outputFileStream.close();
            }
        }
        tarInputStream.close();

        unGzippedFile.delete();
    }

    /**
     * Based on the given mode value, sets the permission on the given file address (windows will be ignored)
     * @param inputFile the file to change permissions for
     * @param mode the new mode for the file e.g. 755
     * @throws IOException if an error happens while setting the permissions on the file
     */
    private static void setPermissions(File inputFile, int mode) throws IOException {
        if (!Files.isSymbolicLink(inputFile.toPath()) && PosixUtil.isPosixFileStore(inputFile)) {
            Set<PosixFilePermission> permissions = PosixUtil.getPosixPermissionsAsSet(mode);
            if (!permissions.isEmpty()) {
                Files.setPosixFilePermissions(inputFile.toPath(), permissions);
            }
        }
    }

    /**
     * Ungzip an input file into an output file. The output file is created in the output folder, having the same name
     * as the input file, minus the '.gz' extension.
     * @param inputFileAddr the input .gz file address
     * @param outputDirAddr the output directory address
     * @throws IOException if the input file or output dir don't exists or something bad happens when ungzipping
     * @return  The {@File} with the ungzipped content.
     */
    private static File unGzip(String inputFileAddr, String outputDirAddr) throws IOException {
        final File inputFile = new File(inputFileAddr);
        final File outputDir = new File(outputDirAddr);
        String outputFileName = inputFile.getName().substring(0, inputFile.getName().length() - 3);
        if (!outputFileName.endsWith(".tar")) {
            outputFileName += ".tar";
        }
        final File outputFile = new File(outputDir, outputFileName);

        outputDir.mkdirs();
        final GZIPInputStream in = new GZIPInputStream(new FileInputStream(inputFile));
        final FileOutputStream out = new FileOutputStream(outputFile);

        IOUtils.copy(in, out);

        in.close();
        out.close();

        return outputFile;
    }

    /**
     * Unzips a zip file into a specified directory maintaining file permissions if the OS allows it
     * @param sourceZip the input .zip file adderss
     * @param outputDir the output directory address
     * @throws IOException if the input file or output dir don't exists or something bad happens when unzipping
     */
    public static void unzip(String sourceZip, String outputDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(sourceZip)) {
            List<ZipArchiveEntry> zipArchiveEntries = Collections.list(zipFile.getEntries());
            for (ZipArchiveEntry zipArchiveEntry : zipArchiveEntries) {
                File extractedFile = new File(outputDir, zipArchiveEntry.getName());
                FileUtils.forceMkdir(extractedFile.getParentFile());
                if (zipArchiveEntry.isUnixSymlink()) {
                    if (PosixUtil.isPosixFileStore(new File(outputDir))) {
                        String symlinkTarget = zipFile.getUnixSymlink(zipArchiveEntry);
                        Files.createSymbolicLink(extractedFile.toPath(), new File(symlinkTarget).toPath());
                    }
                } else if (zipArchiveEntry.isDirectory()) {
                    FileUtils.forceMkdir(extractedFile);
                } else {
                    try (
                            InputStream in = zipFile.getInputStream(zipArchiveEntry);
                            OutputStream out = new FileOutputStream(extractedFile)
                    ) {
                        IOUtils.copy(in, out);
                    }
                }
                setPermissions(extractedFile, zipArchiveEntry.getUnixMode());
            }
        }
    }
}
