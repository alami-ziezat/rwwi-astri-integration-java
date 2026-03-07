package com.rwi.myrepublic.nisa;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration singleton for NISA API integration.
 * Loads configuration from nisa_config.properties on classpath.
 */
public class NisaConfig {

    private static NisaConfig instance;
    private final Properties props;

    private NisaConfig() {
        props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("nisa_config.properties")) {
            if (in != null) {
                props.load(in);
                System.out.println("[NisaConfig] Loaded nisa_config.properties");
            } else {
                System.err.println("[NisaConfig] Warning: nisa_config.properties not found on classpath, using defaults");
            }
        } catch (IOException e) {
            System.err.println("[NisaConfig] Warning: Could not load nisa_config.properties: " + e.getMessage());
        }
    }

    public static NisaConfig getInstance() {
        if (instance == null) {
            synchronized (NisaConfig.class) {
                if (instance == null) {
                    instance = new NisaConfig();
                }
            }
        }
        return instance;
    }

    /** Base URL for all NISA API endpoints. */
    public String getApiBaseUrl() {
        return props.getProperty("nisa.api.base.url",
            "https://apinisa.oss.myrepublic.co.id/api");
    }

    /** Username for /authentication/gettoken. */
    public String getUsername() {
        return props.getProperty("nisa.username", "fms.team");
    }

    /** Password for /authentication/gettoken. */
    public String getPassword() {
        return props.getProperty("nisa.password", "");
    }

    /** HTTP request timeout in milliseconds. */
    public long getRequestTimeout() {
        return Long.parseLong(props.getProperty("nisa.timeout.request", "30000"));
    }

    /** HTTP connection timeout in milliseconds. */
    public long getConnectionTimeout() {
        return Long.parseLong(props.getProperty("nisa.timeout.connection", "10000"));
    }
}
