package com.rwi.myrepublic.stella;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration singleton for Stella API integration.
 * Loads configuration from stella_config.properties on classpath.
 *
 * Split out of AstriConfig - Stella is hosted on a different IP than the
 * rest of ASTRI (see stella.api.base.url), it only shared AstriConfig's
 * singleton for convenience originally. Credentials are currently the same
 * as ASTRI's (confirmed as intentional, not coincidental, at split time).
 */
public class StellaConfig {
    private static StellaConfig instance;
    private Properties props;

    private StellaConfig() {
        props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("stella_config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load stella_config.properties, using defaults");
        }
    }

    public static StellaConfig getInstance() {
        if (instance == null) {
            synchronized (StellaConfig.class) {
                if (instance == null) {
                    instance = new StellaConfig();
                }
            }
        }
        return instance;
    }

    public String getBaseUrl() {
        return props.getProperty("stella.api.base.url",
            "http://172.17.52.160/astri-api-v2/v4");
    }

    public String getUsername() {
        return props.getProperty("stella.username", "smallworld");
    }

    public String getPassword() {
        return props.getProperty("stella.password", "Smallworld@2025!");
    }

    public long getRequestTimeout() {
        return Long.parseLong(props.getProperty("stella.timeout.request", "30000"));
    }

    public long getConnectionTimeout() {
        return Long.parseLong(props.getProperty("stella.timeout.connection", "10000"));
    }
}
