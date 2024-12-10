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
  public static Response wrapOld(Response response) {
      long traceId = -1L;
      int loc;

      final String operationExecutionHeader = response.getHeaderString(HEADER_FIELD);
      if ((operationExecutionHeader == null) || (operationExecutionHeader.equals(""))) {
        System.out.println("Response without tracking id was found");
      } else {

        final String[] headerArray = operationExecutionHeader.split(",");

        // Extract the location in the trace
        final String locStr = headerArray[1];
        loc = -1;
        try {
          loc = Integer.parseInt(locStr);
        } catch (final NumberFormatException exc) {
          System.out.println("Invalid eoi");
          System.out.println(exc);
        }

        // Extract trace id
        final String traceIdStr = headerArray[0];

        if (traceIdStr != null) {
          try {
            traceId = Long.parseLong(traceIdStr);
          } catch (final NumberFormatException exc) {
            System.out.println("Invalid trace id");
            System.out.println(exc);
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

  public static Response wrap(Response response) {

      long traceId = -1L;
      int eoi;
      int ess;
      String parentId;

      final String operationExecutionHeader = response.getHeaderString(HEADER_FIELD);
      if ((operationExecutionHeader == null) || (operationExecutionHeader.equals(""))) {
        System.out.println("Response without tracking id was found");
      } else {

        final String[] headerArray = operationExecutionHeader.split(",");


        // Extract EOI
        final String eoiStr = headerArray[1];
        eoi = -1;
        try {
          eoi = Integer.parseInt(eoiStr);
        } catch (final NumberFormatException exc) {
          System.out.println("Invalid eoi");
          System.out.println(exc);
        }

        // Extract ESS
        final String essStr = headerArray[2];
        ess = -1;
        try {
          ess = Integer.parseInt(essStr);
        } catch (final NumberFormatException exc) {
          System.out.println("Invalid ess");
          System.out.println(exc);
        }

        final String parentIdStr = headerArray[3];
        parentId = parentIdStr;
        System.out.println("In response wrapper the recieved parent id is " + parentId);

        // Extract trace id
        final String traceIdStr = headerArray[0];
        if (traceIdStr != null) {
          try {
            traceId = Long.parseLong(traceIdStr);
          } catch (final NumberFormatException exc) {
            System.out.println("Invalid trace id");
            System.out.println(exc);
          }
        } else {
          traceId = TraceContext.getUniqueTraceId();
          eoi = 0; // EOI of this execution
          ess = 0; // ESS of this execution
          parentId = "NA-response-wrapper";
        }

        // Store thread-local values
        TraceContext.storeThreadLocalTraceId(traceId);
        TraceContext.storeThreadLocalEOI(eoi); // this execution has EOI=eoi; next execution will get
                                              // eoi with incrementAndRecall
        TraceContext.storeThreadLocalESS(ess); // this execution has ESS=ess
        
        // your parent is different form the parent recieved by this call
        // The parent-ID you recieving is your sender id indeed
        TraceContext.storeThreadLocalSenderId(parentId);
      }
    
    return response;
  }

}
