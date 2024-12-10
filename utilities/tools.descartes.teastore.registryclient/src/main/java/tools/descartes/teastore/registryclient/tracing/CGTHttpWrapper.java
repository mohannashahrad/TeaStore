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
  // public static Builder wrapOld(WebTarget target) {
  //   Builder builder = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
  //   Tracing.inject(builder);
  //     long traceId = TraceContext.recallThreadLocalTraceId();
  //     int loc = TraceContext.recallThreadLocalLoc();
  //     if (traceId == -1) {
  //       traceId = TraceContext.getAndStoreUniqueThreadLocalTraceId();
  //       loc = 0;
  //     } 

  //     // this assumes that this was already defined [if not it would be -1 by default]
  //     int senderId = TraceContext.recallThreadLocalSenderId();

  //     // Also include sender Id in the request
  //     return builder.header(HEADER_FIELD,
  //         Long.toString(traceId) + "," + Integer.toString(loc) + "," + Integer.toString(senderId));
  // }

  public static Builder wrap(WebTarget target) {
    Builder builder = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);

      final int eoi; // this is executionOrderIndex-th execution in this trace
      final int ess; // this is the height in the dynamic call tree of this execution
      final int nextESS;
      final String parentId;
      final String senderId;

      long traceId = TraceContext.recallThreadLocalTraceId(); // traceId, -1 if entry point
      if (traceId == -1) {
        // entrypoint = true;
        traceId = TraceContext.getAndStoreUniqueThreadLocalTraceId();
        TraceContext.storeThreadLocalEOI(0);
        TraceContext.storeThreadLocalESS(1); // next operation is ess + 1
        eoi = 0;
        ess = 0;
        nextESS = 1;
        parentId = "NA-wrapper";
        senderId = "NA-wrapper";
        TraceContext.storeThreadLocalParentId(parentId);
        TraceContext.storeThreadLocalParentId(senderId);
      } 
      else {
        // entrypoint = false;
        eoi = TraceContext.recallThreadLocalEOI();
        ess = TraceContext.recallThreadLocalESS();
        // in this case parentId does not matter anymore
        parentId = TraceContext.recallThreadLocalParentId();
        senderId = TraceContext.recallThreadLocalSenderId();
        nextESS = ess;
        if ((eoi == -1) || (ess == -1)) {
          System.out.println("eoi and/or ess have invalid values:" + " eoi == " + eoi + " ess == " + ess);
        }
      }
      // Get request header
      // So here the idea is that the next parentID would be myself, so I should send my curr ID
      return builder.header(HEADER_FIELD,
          Long.toString(traceId)  + "," + Integer.toString(eoi) + "," + Integer.toString(nextESS) +  "," + senderId);
  }
}
