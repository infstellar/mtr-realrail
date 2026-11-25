package mtr.realrail.dispatch;

import mtr.realrail.EmbeddedDispatchService;
import mtr.realrail.MtrRealRail;

/** Queues RealRail commands for the embedded dispatcher delivery thread. */
public final class TscDispatchCommands {

	private TscDispatchCommands() {
	}

	public static boolean send(String kind, String vehicleId, String target, String reason) {
		if (!TscDispatchBridge.isChainActive()) {
			return false;
		}
		final EmbeddedDispatchService service = MtrRealRail.dispatcher();
		if (service == null) {
			return false;
		}
		final String commandTarget = "return_to_depot".equals(kind) ? "" : target;
		return service.enqueue(kind, vehicleId, commandTarget, reason);
	}
}
