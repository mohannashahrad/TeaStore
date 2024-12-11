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

    // TODO: here make sure eoi or loc basically works [depth on the call graph basically, could be a nice thing for later processing]
    // Right now the eimplementation of eoi and ess is not verified to be correct
    public static void trackMethodCall(String trackingId, String method, String path, String requestIp, String hostIp, String parentId, int eoi, int ess) {
        TraceData traceData = new TraceData(trackingId, method, path, requestIp, hostIp, parentId, eoi, ess);
        traceQueue.offer(traceData);
    }

    public static void trackNew(String trackingId, String method, String path, String requestIp, String hostIp, String parentId, int eoi, int ess) {
         try {
        URL url = new URL(TRACKING_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Create the JSON payload with the required fields
        String jsonInputString = String.format(
                "{\"trackingId\": \"%s\", \"method\": \"%s\", \"path\": \"%s\", " +
                "\"requestIp\": \"%s\", \"host\": \"%s\", \"parentId\": \"%s\", \"eoi\": \"%d\", \"ess\": \"%d\"}",
                trackingId, method, path, requestIp, hostIp, parentId, eoi, ess);

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
             System.out.println("Error happened in sending the connection");
              System.out.println(e);
        }
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
                        "{\"trackingId\": \"%s\", \"method\": \"%s\", \"path\": \"%s\", " +
                        "\"requestIp\": \"%s\", \"host\": \"%s\", \"parentId\": \"%s\", \"eoi\": \"%d\", \"ess\": \"%d\"}",
                        traceData.getTrackingId(), traceData.getMethod(), traceData.getPath(),
                        traceData.getRequestIp(), traceData.getHostIp(), traceData.getParentId(),
                        traceData.getEoi(), traceData.getEss());
                
                jsonBatch.append(traceDataJson);

                // Add a comma between objects, but not at the end
                if (i < batch.size() - 1) {
                    jsonBatch.append(",");
                }
            }
            jsonBatch.append("]");

            // Send the batch to the backend in a single POST request
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
                        "{\"trackingId\": \"%s\", \"method\": \"%s\", \"path\": \"%s\", " +
                        "\"requestIp\": \"%s\", \"host\": \"%s\", \"parentId\": \"%s\", \"eoi\": \"%d\", \"ess\": \"%d\"}",
                        traceData.getTrackingId(), traceData.getMethod(), traceData.getPath(),
                        traceData.getRequestIp(), traceData.getHostIp(), traceData.getParentId(),
                        traceData.getEoi(), traceData.getEss());

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
        private final String hostIp;
        private final String parentId;
        private final int eoi;
        private final int ess;

        public TraceData(String trackingId, String method, String path, String requestIp, String hostIp, String parentId,
                         int eoi, int ess) {
            this.trackingId = trackingId;
            this.method = method;
            this.path = path;
            this.requestIp = requestIp;
            this.hostIp = hostIp;
            this.parentId = parentId;
            this.eoi = eoi;
            this.ess = ess;
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

        public String getHostIp() {
            return hostIp;
        }

        public String getParentId() {
            return parentId;
        }

        public int getEoi() {
            return eoi;
        }

        public int getEss() {
            return ess;
        }

         @Override
        public String toString() {
            return "TraceData{" +
                    "trackingId='" + trackingId + '\'' +
                    ", method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", requestIp='" + requestIp + '\'' +
                    ", hostIp='" + hostIp + '\'' +
                    ", parentId='" + parentId + '\'' +
                    ", eoi=" + eoi +
                    ", ess=" + ess +
                    '}';
        }
    }
}