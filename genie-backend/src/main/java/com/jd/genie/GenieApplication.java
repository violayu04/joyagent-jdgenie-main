package com.jd.genie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GenieApplication {
    public static void main(String[] args) {
        // Disable proxy for localhost connections
        System.setProperty("java.net.useSystemProxies", "false");
        System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|*.local");
        System.setProperty("https.nonProxyHosts", "localhost|127.0.0.1|*.local");
        System.setProperty("ftp.nonProxyHosts", "localhost|127.0.0.1|*.local");
        
        // Clear any existing proxy settings
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        
        SpringApplication.run(GenieApplication.class, args);
    }
}