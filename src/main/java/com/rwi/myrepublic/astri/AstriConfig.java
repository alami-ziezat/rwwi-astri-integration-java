package com.rwi.myrepublic.astri;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration singleton for ASTRI API integration.
 * Loads configuration from astri_config.properties on classpath.
 */
public class AstriConfig {
    private static AstriConfig instance;
    private Properties props;

    private AstriConfig() {
        props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("astri_config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load astri_config.properties, using defaults");
        }
    }

    public static AstriConfig getInstance() {
        if (instance == null) {
            synchronized (AstriConfig.class) {
                if (instance == null) {
                    instance = new AstriConfig();
                }
            }
        }
        return instance;
    }

    public String getApiBaseUrl() {
        return props.getProperty("astri.api.base.url",
            "http://172.17.75.22/astri-api-v2/v4");
    }

    public String getDmBaseUrl() {
        return props.getProperty("astri.dm.base.url",
            "http://172.17.75.22/astri-dm/v4");
    }

    public String getUsername() {
        return props.getProperty("astri.username", "smallworld");
    }

    public String getPassword() {
        return props.getProperty("astri.password", "Smallworld@2025!");
    }

    public long getRequestTimeout() {
        return Long.parseLong(props.getProperty("astri.timeout.request", "30000"));
    }

    public long getConnectionTimeout() {
        return Long.parseLong(props.getProperty("astri.timeout.connection", "10000"));
    }

    public String getDownloadDir() {
        // Use SMALLWORLD_GIS environment variable to create dynamic path
        // Save to parent directory: %SMALLWORLD_GIS%/../kml_files
        String smallworldGis = System.getenv("SMALLWORLD_GIS");
        System.out.println("DEBUG: SMALLWORLD_GIS env var: " + smallworldGis);

        if (smallworldGis != null && !smallworldGis.isEmpty()) {
            // Get parent directory of SMALLWORLD_GIS
            java.io.File gisDir = new java.io.File(smallworldGis);
            java.io.File parentDir = gisDir.getParentFile();

            if (parentDir != null) {
                String kmlDir = parentDir.getAbsolutePath() + "/kml_files";
                System.out.println("DEBUG: Using KML directory (parent of SMALLWORLD_GIS): " + kmlDir);
                return kmlDir;
            } else {
                System.out.println("DEBUG: Could not get parent directory, using SMALLWORLD_GIS directly");
                String kmlDir = smallworldGis + "/kml_files";
                return kmlDir;
            }
        }

        // Fallback to config or default
        String defaultDir = props.getProperty("astri.download.dir", "downloads");
        System.out.println("DEBUG: Using default directory: " + defaultDir);
        return defaultDir;
    }
}
