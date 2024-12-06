package tools.descartes.teastore.registryclient.tracing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallGraphTracker {

    // private static Config config;
    // private static Config.TrackingConfig trackingConfig;
    private static String TRACKING_URL = "http://10.9.155.173:8081/track";

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

    public static void trackMethodCall(String trackingId, String method, String path, 
                                   String requestIp, String host, int loc, int parentSenderId, int currSenderId) {
        try {

            // TODO: Ideally this should come from the config file provided by the user
            // First, normalize the path to prevent redundancy in call tracking.
            // patterns of having numbers at the end of the url
            String regex = "(/\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(path);
            String normalizedPath = matcher.replaceAll("/*");

            // Open a connection to the server
            URL url = new URL(TRACKING_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

             // If user specified: set the connect and read timeouts
            //  if (trackingConfig != null) {
            //     connection.setConnectTimeout(trackingConfig.getCgtServerConnectTimeout());  // Set connection timeout (in milliseconds)
            //     connection.setReadTimeout(trackingConfig.getCgtServerReadTimeout());
            //  }

             // @TODO: aslo should make sure that if it fails then stop doing that!

            // Create the JSON payload with the required fields
            String jsonInputString = String.format(
                    "{\"trackingId\": \"%s\", \"method\": \"%s\", \"opIndex\": %d, " +
                    "\"path\": \"%s\", \"requestIp\": \"%s\", \"host\": \"%s\", \"parentSenderId\": \"%d\", \"currSenderId\": \"%d\"}",
                    trackingId, method, loc, 
                    normalizedPath != null ? normalizedPath : "",  
                    requestIp != null ? requestIp : "",            
                    host != null ? host : "",
                    parentSenderId, currSenderId               
            );

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // If response code is 400 (Bad Request), read the response body for details
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

            // Close the connection
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}