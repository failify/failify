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
 */

package me.arminb.spidersilk.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;

// Copied from https://stackoverflow.com/questions/17641706/how-to-copy-a-directory-with-its-attributes-permissions-from-one-location-to-ano/18691793#18691793
// TODO find a better way to do this
public class FileUtil {
    public static void copyDirectory(final Path source, final Path target)
            throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE, new FileVisitor<Path>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir,
                                                             BasicFileAttributes sourceBasic) throws IOException {
                        Path targetDir = Files.createDirectories(target.resolve(source.relativize(dir)));
                        AclFileAttributeView acl = Files.getFileAttributeView(dir,
                                AclFileAttributeView.class);
                        if (acl != null)
                            Files.getFileAttributeView(targetDir,
                                    AclFileAttributeView.class).setAcl(acl.getAcl());
                        DosFileAttributeView dosAttrs = Files.getFileAttributeView(
                                dir, DosFileAttributeView.class);
                        if (dosAttrs != null) {
                            DosFileAttributes sourceDosAttrs = dosAttrs
                                    .readAttributes();
                            DosFileAttributeView targetDosAttrs = Files
                                    .getFileAttributeView(targetDir,
                                            DosFileAttributeView.class);
                            targetDosAttrs.setArchive(sourceDosAttrs.isArchive());
                            targetDosAttrs.setHidden(sourceDosAttrs.isHidden());
                            targetDosAttrs.setReadOnly(sourceDosAttrs.isReadOnly());
                            targetDosAttrs.setSystem(sourceDosAttrs.isSystem());
                        }
                        FileOwnerAttributeView ownerAttrs = Files
                                .getFileAttributeView(dir, FileOwnerAttributeView.class);
                        if (ownerAttrs != null) {
                            FileOwnerAttributeView targetOwner = Files
                                    .getFileAttributeView(targetDir,
                                            FileOwnerAttributeView.class);
                            targetOwner.setOwner(ownerAttrs.getOwner());
                        }
                        PosixFileAttributeView posixAttrs = Files
                                .getFileAttributeView(dir, PosixFileAttributeView.class);
                        if (posixAttrs != null) {
                            PosixFileAttributes sourcePosix = posixAttrs
                                    .readAttributes();
                            PosixFileAttributeView targetPosix = Files
                                    .getFileAttributeView(targetDir,
                                            PosixFileAttributeView.class);
                            targetPosix.setPermissions(sourcePosix.permissions());
                            targetPosix.setGroup(sourcePosix.group());
                        }
                        UserDefinedFileAttributeView userAttrs = Files
                                .getFileAttributeView(dir,
                                        UserDefinedFileAttributeView.class);
                        if (userAttrs != null) {
                            UserDefinedFileAttributeView targetUser = Files
                                    .getFileAttributeView(targetDir,
                                            UserDefinedFileAttributeView.class);
                            for (String key : userAttrs.list()) {
                                ByteBuffer buffer = ByteBuffer.allocate(userAttrs
                                        .size(key));
                                userAttrs.read(key, buffer);
                                buffer.flip();
                                targetUser.write(key, buffer);
                            }
                        }
                        // Must be done last, otherwise last-modified time may be
                        // wrong
                        BasicFileAttributeView targetBasic = Files
                                .getFileAttributeView(targetDir,
                                        BasicFileAttributeView.class);
                        targetBasic.setTimes(sourceBasic.lastModifiedTime(),
                                sourceBasic.lastAccessTime(),
                                sourceBasic.creationTime());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file,
                                                     BasicFileAttributes attrs) throws IOException {
                        Files.copy(file, target.resolve(source.relativize(file)),
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult
                    visitFileFailed(Path file, IOException e)
                            throws IOException {
                        throw e;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir,
                                                              IOException e) throws IOException {
                        if (e != null) throw e;
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
