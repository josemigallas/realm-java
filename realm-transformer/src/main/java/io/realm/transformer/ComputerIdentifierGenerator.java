/*
 * Copyright 2015 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.transformer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;


/**
 * Generate a unique identifier for a computer. The method being used depends on the platform:
 *  - OS X:  Apple Serial Number
 *  - Windows:  BIOS identifier
 *  - Linux: Mac address of the first device (this is flimsy by nature and impossible to fix without root access)
 */
public class ComputerIdentifierGenerator {

    private static final String UNKNOWN = "unknown";

    private static String OS = System.getProperty("os.name").toLowerCase();

    public static String get() {
        if (isWindows()) {
            return getWindowsIdentifier();
        } else if (isMac()) {
            return getMacOsIdentifier();
        } else if (isLinux()) {
            return getLinuxMacAddress();
        } else {
            return UNKNOWN;
        }
    }

    private static boolean isWindows() {
        return (OS.contains("win"));
    }

    private static boolean isMac() {
        return (OS.contains("mac"));
    }

    private static boolean isLinux() {
        return (OS.contains("nux"));
    }

    private static String getLinuxMacAddress() {
        File machineId = new File("/var/lib/dbus/machine-id");
        if (!machineId.exists()) {
            machineId = new File("/etc/machine-id");
        }
        if (!machineId.exists()) {
            return UNKNOWN;
        }
        
        Scanner scanner = null;
        try {
            scanner = new Scanner(machineId);
            String id = scanner.useDelimiter("\\A").next();
            return Utils.hexStringify(Utils.sha256Hash(id.getBytes()));
        } catch (FileNotFoundException e) {
            return UNKNOWN;
        } catch (NoSuchAlgorithmException e) {
            return UNKNOWN;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    private static String getMacOsIdentifier() {
        NetworkInterface networkInterface;
        try {
            networkInterface = NetworkInterface.getByName("en0");
        } catch (SocketException e) {
            return UNKNOWN;
        }

        byte[] hardwareAddress;
        try {
            hardwareAddress = networkInterface.getHardwareAddress(); // Normally this is the MAC address
        } catch (SocketException e) {
            return UNKNOWN;
        }

        try {
            return Utils.hexStringify(Utils.sha256Hash(hardwareAddress));
        } catch (NoSuchAlgorithmException e) {
            return UNKNOWN;
        }
    }

    private static String getWindowsIdentifier() {
        Runtime runtime = Runtime.getRuntime();
        Process process;
        String result = UNKNOWN;

        try {
            process = runtime.exec(new String[] { "wmic", "bios", "get", "serialnumber" });
        } catch (IOException e) {
            return result;
        }

        InputStream is = process.getInputStream();

        Scanner sc = new Scanner(is);
        try {
            while (sc.hasNext()) {
                String next = sc.next();
                if ("SerialNumber".equals(next)) {
                    result = sc.next().trim();
                    break;
                }
            }
        } finally {
            try {
                is.close();
            } catch (IOException ignore) {
                // Ignored not to break the Transformer
            }
        }

        return result;
    }
}