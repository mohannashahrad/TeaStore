package tools.descartes.teastore.registryclient.tracing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class CallGraphTracker {

    // private static Config config;
    // private static Config.TrackingConfig trackingConfig;
    //private static String TRACKING_URL = "http://128.105.145.131:8081/track";
    private static String TRACKING_URL = "";
    private static int TRACKING_FREQ = 0 ;
    private static int TRACKING_BATCH_SIZE = 0;
    static {
        try {
            TRACKING_URL = (String) new InitialContext().lookup("java:comp/env/cgtURL");
            System.out.println("CGT_URL has been found");
            System.out.println(TRACKING_URL);
        } catch (NamingException e) {
            System.out.println("CGT_URL not set");
        }

         try {
            String freq = (String) new InitialContext().lookup("java:comp/env/cgtFreq");
            TRACKING_FREQ = Integer.parseInt(freq);
            System.out.println("cgtFreq has been found");
            System.out.println(TRACKING_FREQ);
        } catch (Exception e) {
            System.out.println("cgtFreq not set");
            System.out.println(e);
        }

         try {
            String bSize = (String) new InitialContext().lookup("java:comp/env/cgtBatch");
            TRACKING_BATCH_SIZE = Integer.parseInt(bSize);
            System.out.println("cgtBatch has been found");
            System.out.println(TRACKING_BATCH_SIZE);
        } catch (Exception e) {
            System.out.println("cgtBatch not set");
            System.out.println(e);
        }
    }
    private static final Queue<TraceData> traceQueue = new ConcurrentLinkedQueue<>();


    // static {
    //     // Load the configuration when the class is loaded
    //     String configFilePath = System.getenv("CGT_CONFIG_PATH");   // this is already set as an env variable in the docker-compose file
    //     config = Config.loadConfig(configFilePath);
    //     if (config != null && config.getTracking() != null) {
    //         trackingConfig = config.getTracking();
    //         TRACKING_URL = trackingConfig.getCgtServerUrl() + "/track";
    //     } else {
    //         // Handle the case where the config loading fails
    //         System.err.println("Failed to load configuration. Please check the config file.");
    //         // You can throw an exception here if you want to stop further processing
    //         throw new RuntimeException("Configuration loading failed.");
    //     }
    // }

    // public static void trackMethodCall(String trackingId, String method, String path, 
    //                                String requestIp, String host, int loc, int parentSenderId, int currSenderId) {
    //     try {

    //         // TODO: Ideally this should come from the config file provided by the user
    //         // First, normalize the path to prevent redundancy in call tracking.
    //         // patterns of having numbers at the end of the url
    //         String regex = "(/\\d+)";
    //         Pattern pattern = Pattern.compile(regex);
    //         Matcher matcher = pattern.matcher(path);
    //         String normalizedPath = matcher.replaceAll("/*");

    //         // Open a connection to the server
    //         URL url = new URL(TRACKING_URL);
    //         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    //         connection.setRequestMethod("POST");
    //         connection.setRequestProperty("Content-Type", "application/json");
    //         connection.setDoOutput(true);

    //          // If user specified: set the connect and read timeouts
    //         //  if (trackingConfig != null) {
    //         //     connection.setConnectTimeout(trackingConfig.getCgtServerConnectTimeout());  // Set connection timeout (in milliseconds)
    //         //     connection.setReadTimeout(trackingConfig.getCgtServerReadTimeout());
    //         //  }

    //          // @TODO: aslo should make sure that if it fails then stop doing that!

    //         // Create the JSON payload with the required fields
    //         String jsonInputString = String.format(
    //                 "{\"trackingId\": \"%s\", \"method\": \"%s\", \"opIndex\": %d, " +
    //                 "\"path\": \"%s\", \"requestIp\": \"%s\", \"host\": \"%s\", \"parentSenderId\": \"%d\", \"currSenderId\": \"%d\"}",
    //                 trackingId, method, loc, 
    //                 normalizedPath != null ? normalizedPath : "",  
    //                 requestIp != null ? requestIp : "",            
    //                 host != null ? host : "",
    //                 parentSenderId, currSenderId               
    //         );

    //         try (OutputStream os = connection.getOutputStream()) {
    //             byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
    //             os.write(input, 0, input.length);
    //         }

    //         int responseCode = connection.getResponseCode();
    //         // System.out.println("Response Code: " + responseCode);

    //         // If response code is 400 (Bad Request), read the response body for details
    //         if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
    //             try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
    //                 String inputLine;
    //                 StringBuilder response = new StringBuilder();
    //                 while ((inputLine = in.readLine()) != null) {
    //                     response.append(inputLine);
    //                 }
    //                 System.out.println("Error Response: " + response.toString());
    //             }
    //         }

    //         // Close the connection
    //         connection.disconnect();

    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    // }

    public static void trackMethodCall(String trackingId, String method, String path, 
                                   String requestIp, String host, int loc, int parentSenderId, int currSenderId) {
        TraceData traceData = new TraceData(trackingId, method, path, requestIp, host, loc, parentSenderId, currSenderId);
        traceQueue.offer(traceData);
    }


    // Background thread that sends trace data to the backend periodically
    public static void startBackgroundTracking() {
        // Create a background thread that runs every second
        new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(TRACKING_FREQ);
                    
                    // Send all pending trace data to the backend
                    sendTraceDataToBackend();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    // Sends the trace data to the backend [every N seconds, 1 req per 1 trace]
    // private static void sendTraceDataToBackend() {
    //     while (!traceQueue.isEmpty()) {
    //         TraceData traceData = traceQueue.poll();  
    //         sendTraceData(traceData);
    //     }
    // }

    private static void sendTraceDataToBackend() {
        List<TraceData> batch = new ArrayList<>();
        int batchSize = TRACKING_BATCH_SIZE; 
        
        while (!traceQueue.isEmpty() && batch.size() < batchSize) {
            TraceData traceData = traceQueue.poll();
            batch.add(traceData);
        }

        if (!batch.isEmpty()) {
            sendBatchToBackend(batch); 
        }
    }

    private static void sendBatchToBackend(List<TraceData> batch) {
        try {
            // Create the JSON payload for the batch request
            StringBuilder jsonBatch = new StringBuilder("[");
            
            for (int i = 0; i < batch.size(); i++) {
                TraceData traceData = batch.get(i);
                String traceDataJson = String.format(
                        "{\"trackingId\": \"%s\", \"method\": \"%s\", \"opIndex\": %d, " +
                        "\"path\": \"%s\", \"requestIp\": \"%s\", \"host\": \"%s\", \"parentSenderId\": \"%d\", \"currSenderId\": \"%d\"}",
                        traceData.getTrackingId(), traceData.getMethod(), traceData.getLoc(),
                        traceData.getPath(), traceData.getRequestIp(), traceData.getHost(),
                        traceData.getParentSenderId(), traceData.getCurrSenderId());
                jsonBatch.append(traceDataJson);

                // Add a comma between objects, but not at the end
                if (i < batch.size() - 1) {
                    jsonBatch.append(",");
                }
            }
            jsonBatch.append("]");

            // Send the batch to the backend in a single POST request
            System.out.println("Before sending the traces the url is");
            System.out.println(TRACKING_URL);
            URL url = new URL(TRACKING_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBatch.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    System.out.println("Error Response: " + response.toString());
                }
            }

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Send a single trace data object to the backend
    private static void sendTraceData(TraceData traceData) {
        try {
            // Send the data to the backend via HTTP (as shown in your original example)
            URL url = new URL(TRACKING_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Create the JSON payload with the required fields
            String jsonInputString = String.format(
                    "{\"trackingId\": \"%s\", \"method\": \"%s\", \"opIndex\": %d, " +
                    "\"path\": \"%s\", \"requestIp\": \"%s\", \"host\": \"%s\", \"parentSenderId\": \"%d\", \"currSenderId\": \"%d\"}",
                    traceData.getTrackingId(), traceData.getMethod(), traceData.getLoc(),
                    traceData.getPath(), traceData.getRequestIp(), traceData.getHost(),
                    traceData.getParentSenderId(), traceData.getCurrSenderId());

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    System.out.println("Error Response: " + response.toString());
                }
            }

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Inner class to represent trace data
    static class TraceData {
        private final String trackingId;
        private final String method;
        private final String path;
        private final String requestIp;
        private final String host;
        private final int loc;
        private final int parentSenderId;
        private final int currSenderId;

        public TraceData(String trackingId, String method, String path, String requestIp, String host, int loc,
                         int parentSenderId, int currSenderId) {
            this.trackingId = trackingId;
            this.method = method;
            this.path = path;
            this.requestIp = requestIp;
            this.host = host;
            this.loc = loc;
            this.parentSenderId = parentSenderId;
            this.currSenderId = currSenderId;
        }

        public String getTrackingId() {
            return trackingId;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public String getRequestIp() {
            return requestIp;
        }

        public String getHost() {
            return host;
        }

        public int getLoc() {
            return loc;
        }

        public int getParentSenderId() {
            return parentSenderId;
        }

        public int getCurrSenderId() {
            return currSenderId;
        }
    }
}