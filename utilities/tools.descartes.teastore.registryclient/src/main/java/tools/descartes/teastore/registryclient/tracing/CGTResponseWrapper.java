package tools.descartes.teastore.registryclient.tracing;

import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for http responses.
 *
 * @author Simon
 *
 */
public final class CGTResponseWrapper {
  private static final String HEADER_FIELD = "CallGraphTrackingTracing";
  private static final Logger LOG = LoggerFactory.getLogger(CGTResponseWrapper.class);

  /**
   * Hide default constructor.
   */
  private CGTResponseWrapper() {

  } 

  /**
   * Hook for monitoring.
   *
   * @param response
   *          response
   * @return response response
   */
  public static Response wrap(Response response) {
      long traceId = -1L;
      int loc;

      final String operationExecutionHeader = response.getHeaderString(HEADER_FIELD);
      if ((operationExecutionHeader == null) || (operationExecutionHeader.equals(""))) {
        LOG.warn("Response without tracking id was found");
      } else {

        final String[] headerArray = operationExecutionHeader.split(",");

        // Extract the location in the trace
        final String locStr = headerArray[1];
        loc = -1;
        try {
          loc = Integer.parseInt(locStr);
        } catch (final NumberFormatException exc) {
          LOG.warn("Invalid eoi", exc);
        }

        // Extract trace id
        final String traceIdStr = headerArray[0];

        if (traceIdStr != null) {
          try {
            traceId = Long.parseLong(traceIdStr);
          } catch (final NumberFormatException exc) {
            LOG.warn("Invalid trace id", exc);
          }
        } else {
          traceId = TraceContext.getUniqueTraceId();
          loc = 0;
        }

        // Store thread-local values
        TraceContext.storeThreadLocalTraceId(traceId);
        TraceContext.storeThreadLocalLoc(loc);
      }
    return response;
  }

}
