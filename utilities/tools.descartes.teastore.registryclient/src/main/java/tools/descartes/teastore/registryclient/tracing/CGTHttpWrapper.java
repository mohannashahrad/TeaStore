package tools.descartes.teastore.registryclient.tracing;

import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.WebTarget;

/**
 * Wrapper for http calls.
 *
 * @author Simon
 *
 */
public final class CGTHttpWrapper {

  // @TODO: Make the logging solid and test it
  private static final Logger LOG = LoggerFactory.getLogger(CGTHttpWrapper.class);
  private static final String HEADER_FIELD = "CallGraphTrackingTracing";

  /**
   * Hide default constructor.
   */
  private CGTHttpWrapper() {

  }

  /**
   * Wrap webtarget.
   *
   * @param target webtarget to wrap
   * @return wrapped wentarget
   */
  public static Builder wrap(WebTarget target) {
    Builder builder = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
    Tracing.inject(builder);
      long traceId = TraceContext.recallThreadLocalTraceId();
      int loc = TraceContext.recallThreadLocalLoc();
      if (traceId == -1) {
        traceId = TraceContext.getAndStoreUniqueThreadLocalTraceId();
        loc = 0;
      } 

      // this assumes that this was already defined [if not it would be -1 by default]
      int senderId = TraceContext.recallThreadLocalSenderId();

      // Also include sender Id in the request
      return builder.header(HEADER_FIELD,
          Long.toString(traceId) + "," + Integer.toString(loc) + "," + Integer.toString(senderId));
  }
}
