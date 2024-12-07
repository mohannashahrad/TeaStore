package tools.descartes.teastore.registryclient.tracing;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.descartes.teastore.registryclient.rest.CharResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebFilter("/*")  // Apply the filter to all URLs (can be adjusted as needed)
public class TracingFilter implements Filter {

    private static final String HEADER_FIELD = "CallGraphTrackingTracing";

    // I should first figure out how to get the logging to work in containers
    private static final Logger LOG = LoggerFactory.getLogger(TracingFilter.class);


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code if needed
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
    
            long traceId;
            int loc;
            int parentSenderId = -1;
            int currSenderId = -1;
    
            final String operationExecutionHeader = req.getHeader(HEADER_FIELD);
            if (operationExecutionHeader == null || operationExecutionHeader.isEmpty()) {
                LOG.debug("No monitoring data found in the incoming request header");
                traceId = TraceContext.getUniqueTraceId(); // Generate new trace ID
                loc = 0;
                parentSenderId = -1;
                currSenderId = 0;
            } else {
                LOG.debug("Received request: " + req.getMethod() + " with header = " + operationExecutionHeader);
                String traceIdStr = operationExecutionHeader.split(",")[0];
                try {
                    traceId = Long.parseLong(traceIdStr); // Extract trace ID
                } catch (NumberFormatException exc) {
                    LOG.warn("Invalid trace id", exc);
                    traceId = TraceContext.getUniqueTraceId(); // Generate new trace ID if invalid
                }

                String locStr = operationExecutionHeader.split(",")[1];
                try {
                    loc = Integer.parseInt(locStr) + 1; // Extract trace ID
                } catch (NumberFormatException exc) {
                    LOG.warn("Invalid loc index", exc);
                    loc = 0;
                }

                String parentSenderIdStr = operationExecutionHeader.split(",")[2];
                try {
                    parentSenderId = Integer.parseInt(parentSenderIdStr);
                    currSenderId = parentSenderId + 1;
                } catch (NumberFormatException exc) {
                    LOG.warn("Invalid loc index", exc);
                    loc = 0;
                }

            }
    
            // Store trace ID in thread-local
            TraceContext.storeThreadLocalTraceId(traceId);
            TraceContext.storeThreadLocalLoc(loc);
            TraceContext.storeThreadLocalSenderId(currSenderId);
    
            // Wrap response to modify it before sending to client
            // @TODO: I still don't know why this is needed
            CharResponseWrapper wrappedResponse = new CharResponseWrapper(resp);
    
            try {
                // Proceed with the filter chain
                chain.doFilter(request, wrappedResponse);
            } finally {
                // Add tracing info to response header
                // @NOTE: Not sure if I should include sender id here
                wrappedResponse.addHeader(HEADER_FIELD, Long.toString(traceId) + "," + Integer.toString(loc) + "," + Integer.toString(currSenderId));
            }

            // before writing out the response, notify the CallGraphTracker
            // @TODO: with some frequency the calls to the server should be made
            CallGraphTracker.trackMethodCall(Long.toString(traceId), req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getLocalAddr(), loc, parentSenderId, currSenderId);
            
            // Call the tracker only if you were the parent
            // if (parentSenderId == -1) {
            //     System.out.println("Calling the tracker");
            //     CallGraphTracker.trackMethodCall(Long.toString(traceId), req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getLocalAddr(), loc, parentSenderId, currSenderId);
            // }

            // Write wrapped response content to output
            PrintWriter out = resp.getWriter();
            out.write(wrappedResponse.toString());
        } else {
            LOG.error("Invalid request/response types");
        }
    }

    @Override
    public void destroy() {
        // Cleanup code if needed
        TraceContext.unsetThreadLocalTraceId();
    }
}