package tools.descartes.teastore.registryclient.tracing;

import java.io.FileInputStream;
import java.io.InputStream;
import org.yaml.snakeyaml.Yaml;

public class Config {

    private TrackingConfig tracking;

    // Getters and setters
    public TrackingConfig getTracking() {
        return tracking;
    }

    public void setTracking(TrackingConfig tracking) {
        this.tracking = tracking;
    }

    public static class TrackingConfig {
        private String cgtServerUrl;
        private int cgtServerConnectTimeout;
        private int cgtServerReadTimeout;

        // Constructor is used to set the default values
        public TrackingConfig() {
            this.cgtServerUrl = "http://localhost:8080";
            this.cgtServerConnectTimeout = 5000;
            this.cgtServerReadTimeout = 5000;
        }

        // Getters and setters
        public String getCgtServerUrl() {
            return cgtServerUrl;
        }

        public void setCgtServerUrl(String cgtServerUrl) {
            this.cgtServerUrl = cgtServerUrl;
        }

        public int getCgtServerConnectTimeout() {
            return cgtServerConnectTimeout;
        }

        public void setCgtServerConnectTimeout(int cgtServerConnectTimeout) {
            this.cgtServerConnectTimeout = cgtServerConnectTimeout;
        }

        public int getCgtServerReadTimeout() {
            return cgtServerReadTimeout;
        }

        public void setCgtServerReadTimeout(int cgtServerReadTimeout) {
            this.cgtServerReadTimeout = cgtServerReadTimeout;
        }
    }

    // @NOTE: Assumption here is that config file will be co-located with the rest of the tracking classes
    public static Config loadConfig(String filePath) {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(filePath)) {
            return yaml.loadAs(in, Config.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // public static void main(String[] args) {
    //     Config config = Config.loadConfig("cgt_config.yaml");
    //     System.out.println(config);
    //     Config.TrackingConfig trackingConfig = config.getTracking();
    //     System.out.println(trackingConfig.getCgtServerUrl());
    // }
}
