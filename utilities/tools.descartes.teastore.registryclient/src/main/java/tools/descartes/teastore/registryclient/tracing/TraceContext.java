package tools.descartes.teastore.registryclient.tracing;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

// Making sure that this is signleton, so there is only one version of this in the class
public class TraceContext {
    
    private static transient final ThreadLocal<Long> traceId = new ThreadLocal<>();
    private static final ThreadLocal<Integer> loc = new ThreadLocal<>();
	private static final transient ThreadLocal<Integer> threadLocalEoi = new ThreadLocal<>();
	private static final transient ThreadLocal<Integer> threadLocalEss = new ThreadLocal<>();
	private static final transient ThreadLocal<String> threadLocalParentId = new ThreadLocal<>();
	private static final transient ThreadLocal<String> senderId = new ThreadLocal<>();
    private static final AtomicLong lastThreadId = new AtomicLong((long) new Random().nextInt(65536) << (Long.SIZE - 16 - 1));

    // Set trace ID in the thread local context
    public static void storeThreadLocalTraceId(final long traceId) {
        TraceContext.traceId.set(traceId);
    }

    // Set Loc in the thread local context [Location of the func in the trace]
    public static void storeThreadLocalLoc(final int loc) {
        TraceContext.loc.set(loc);
    }

    public static void storeThreadLocalSenderId(final String senderId) {
        TraceContext.senderId.set(senderId);
    }

    // Get the trace ID from the thread local context
    public static long getTraceId() {
        return traceId.get();
    }

    public static long getAndStoreUniqueThreadLocalTraceId() {
		final long id = TraceContext.getUniqueTraceId();
		TraceContext.traceId.set(id);
		return id;
	}

    // TODO: here maybe I can do something smart for getting the tracing id
    public static long getUniqueTraceId() {
        final long id = TraceContext.lastThreadId.incrementAndGet();
		// Since we use -1 as a marker for an invalid traceId, it must not be returned!
		if (id == -1) {
			// in this case, choose a valid threadId. Note, that this is not necessarily 0 due to concurrent executions of this method.
			//
			// Example: like the following one, but it seems to fine:
			//
			// (this.lastThreadId = -2) Thread A: id = -1 (inc&get -2)
			// (this.lastThreadId = -1) Thread B: id = 0 (inc&get -1)
			// (this.lastThreadId = 0) Thread A: returns 1 (because id == -1, and this.lastThreadId=0 in the meantime)
			// (this.lastThreadId = 1) Thread B: returns 0 (because id != -1)
			return TraceContext.lastThreadId.incrementAndGet();
		} else { 
			return id;
		}
    }

    public static long recallThreadLocalTraceId() {
		final Long traceIdObj = TraceContext.traceId.get();
		if (traceIdObj == null) {
			return -1;
		}
		return traceIdObj;
	}

    public static int recallThreadLocalLoc() {
		final Integer locObj = TraceContext.loc.get();
		if (locObj == null) {
			return -1;
		}
		return locObj;
	}

    public static void unsetThreadLocalTraceId() {
		TraceContext.traceId.remove();
	}

	public static void storeThreadLocalEOI(final int eoi) {
		TraceContext.threadLocalEoi.set(eoi);
	}

	public static int incrementAndRecallThreadLocalEOI() {
		final Integer curEoi = TraceContext.threadLocalEoi.get();
		if (curEoi == null) {
			System.out.println("eoi has not been registered before");
			return -1;
		}
		final int newEoi = curEoi + 1;
		TraceContext.threadLocalEoi.set(newEoi);
		return newEoi;
	}

	public static int recallThreadLocalEOI() {
		final Integer curEoi = TraceContext.threadLocalEoi.get();
		if (curEoi == null) {
			System.out.println("eoi has not been registered before");
			return -1;
		}
		return curEoi;
	}

	public static void unsetThreadLocalEOI() {
		TraceContext.threadLocalEoi.remove();
	}

	public static void storeThreadLocalESS(final int ess) {
		TraceContext.threadLocalEss.set(ess);
	}

	public static void storeThreadLocalParentId(final String parentIdStr) {
		TraceContext.threadLocalParentId.set(parentIdStr);
	}

	public static int recallAndIncrementThreadLocalESS() {
		final Integer curEss = TraceContext.threadLocalEss.get();
		if (curEss == null) {
			System.out.println("ess has not been registered before");
			return -1;
		}
		TraceContext.threadLocalEss.set(curEss + 1);
		return curEss;
	}

	public static int recallThreadLocalESS() {
		final Integer ess = TraceContext.threadLocalEss.get();
		if (ess == null) {
			System.out.println("ess has not been registered before");
			return -1;
		}
		return ess;
	}

	public static String recallThreadLocalParentId() {
		final String parentId = TraceContext.threadLocalParentId.get();
		if (parentId == null) {
			return "NA-recall";
		}
		return parentId;
	}
	
	public static String recallThreadLocalSenderId() {
		final String senderId = TraceContext.senderId.get();
		if (senderId == null) {
			return "NA-recall";
		}
		return senderId;
	}

	public static void unsetThreadLocalESS() {
		TraceContext.threadLocalEss.remove();
	}

	public static void unsetThreadLocalParentId() {
		TraceContext.threadLocalParentId.remove();
	}

	public static void unsetThreadLocalSenderId() {
		TraceContext.senderId.remove();
	}
}