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
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            String url = ((HttpServletRequest) request).getRequestURL().toString();
            // if (url.contains("webui")) {
            // chain.doFilter(request, response);
            // return;
            // }
            HttpServletRequest req = (HttpServletRequest) request;
            long traceId = -1L;
            int eoi;
            int ess;
            String parentId;
            String senderId;

            final String operationExecutionHeader = req.getHeader(HEADER_FIELD);

            if ((operationExecutionHeader == null) || (operationExecutionHeader.equals(""))) {
            // System.out.println("No monitoring data found in the incoming request header");
            traceId = TraceContext.getAndStoreUniqueThreadLocalTraceId();
            TraceContext.storeThreadLocalEOI(0);
            TraceContext.storeThreadLocalESS(1); 
            eoi = 0;
            ess = 0;
            parentId = "NA";
            senderId = req.getRequestURI();
            TraceContext.storeThreadLocalParentId(parentId);
            TraceContext.storeThreadLocalSenderId(senderId);
            } else {
            // System.out.println("Received request: " + req.getMethod() + "with header = " + operationExecutionHeader);
            final String[] headerArray = operationExecutionHeader.split(",");

            // Extract EOI
            final String eoiStr = headerArray[1];
            eoi = -1;
            try {
                eoi = Integer.parseInt(eoiStr);
            } catch (final NumberFormatException exc) {
                LOG.warn("Invalid eoi", exc);
            }

            // Extract ESS
            final String essStr = headerArray[2];
            ess = -1;
            try {
                ess = Integer.parseInt(essStr);
            } catch (final NumberFormatException exc) {
                LOG.warn("Invalid ess", exc);
            }

            final String parentIDStr = headerArray[3];
            parentId = parentIDStr;
            // Sender Id has nothing to do with header context
            senderId = req.getRequestURI();

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
                eoi = 0; // EOI of this execution
                ess = 0; // ESS of this execution
                parentId = "NA";
                senderId = req.getRequestURI();
            }

            // Store thread-local values
            TraceContext.storeThreadLocalTraceId(traceId);
            TraceContext.storeThreadLocalEOI(eoi);
            TraceContext.storeThreadLocalESS(ess);
            TraceContext.storeThreadLocalParentId(parentId);
            TraceContext.storeThreadLocalSenderId(senderId);
            }

        } else {
            LOG.error("Something went wrong");
        }
        CharResponseWrapper wrappedResponse = new CharResponseWrapper((HttpServletResponse) response);
        PrintWriter out = response.getWriter();

        HttpServletRequest req = (HttpServletRequest) request;

        // this is not working for some reason
        CallGraphTracker.trackMethodCall(Long.toString(TraceContext.recallThreadLocalTraceId()), req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getLocalAddr(), TraceContext.recallThreadLocalParentId(), TraceContext.recallThreadLocalEOI(), TraceContext.recallThreadLocalESS());
        //CallGraphTracker.trackNew(Long.toString(TraceContext.recallThreadLocalTraceId()), req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getLocalAddr(), TraceContext.recallThreadLocalParentId(), TraceContext.recallThreadLocalEOI(), TraceContext.recallThreadLocalESS());

        chain.doFilter(request, wrappedResponse);

        long traceId = TraceContext.recallThreadLocalTraceId();
        int eoi = TraceContext.recallThreadLocalEOI();
        String currSenderId =  TraceContext.recallThreadLocalSenderId();

        // if you are sending the reponse back, then the parent Id will be you not the current parent id anymore
        // here parentId is really the parentID of the service still [in the same filter]
        wrappedResponse.addHeader(HEADER_FIELD,
            traceId + "," + (eoi) + "," + Integer.toString(TraceContext.recallThreadLocalESS()) + "," + TraceContext.recallThreadLocalParentId());
        out.write(wrappedResponse.toString());
    }

    @Override
    public void destroy() {
        // Cleanup code if needed
        TraceContext.unsetThreadLocalTraceId();
        TraceContext.unsetThreadLocalEOI();
        TraceContext.unsetThreadLocalESS();
        TraceContext.unsetThreadLocalParentId();
        TraceContext.unsetThreadLocalSenderId();
    }
}