/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.utils;

public class OSUtils {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("linux") || OS_NAME.contains("nix") || OS_NAME.contains("nux");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }
}