package com.luoye.dpt.util;

import com.luoye.dpt.config.Const;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.Manifest;

public class DptBuildKey {

    private static final String MANIFEST_BUILD_KEY_ATTR = "Dpt-Build-Key";

    private DptBuildKey() {
    }

    public static String getVersion() {
        Package pkg = DptBuildKey.class.getPackage();
        String version = (pkg != null) ? pkg.getImplementationVersion() : null;
        if (version == null) {
            version = "unknown";
        }
        return version;
    }

    public static String getBuildKey() {
        // Prefer the key written next to shell SO artifacts so encrypt side
        // always matches the native binary being packaged.
        String executablePath = FileUtils.getExecutablePath();
        if (executablePath != null && !executablePath.isEmpty()) {
            File keyFile = new File(executablePath,
                    "shell-files" + File.separator + Const.KEY_BUILD_KEY_FILE_NAME);
            if (keyFile.isFile()) {
                try {
                    String value = new String(IoUtils.readFile(keyFile.getAbsolutePath()), StandardCharsets.UTF_8).trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                } catch (Exception ignored) {
                    // fall through to jar manifest
                }
            }
        }

        try {
            ClassLoader classLoader = DptBuildKey.class.getClassLoader();
            if (classLoader == null) {
                return null;
            }
            Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try (InputStream is = resources.nextElement().openStream()) {
                    String value = new Manifest(is).getMainAttributes().getValue(MANIFEST_BUILD_KEY_ATTR);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return null;
    }
}
