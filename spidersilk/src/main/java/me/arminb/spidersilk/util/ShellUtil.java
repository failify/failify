package me.arminb.spidersilk.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class ShellUtil {
    public static String getCurrentShellAddress() {
        try {
            Process shell = new ProcessBuilder().command("sh", "-c", "echo $SHELL").start();
            return new Scanner(shell.getInputStream()).nextLine();
        } catch (IOException e) {
            return null;
        }
    }
}
