package mtr.realrail.dispatch;

import mtr.realrail.EmbeddedDispatchService;
import mtr.realrail.MtrRealRail;
import mtr.realrail.RealRailConfig;
import org.mtbridge.forge.EmbeddedBridge;
import org.tsc.dispatch.Service;
import org.tsc.dispatch.util.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** NeoForge view of the dispatcher and control bridge bundled in RealRail. */
public final class TscDispatchBridge {

	public record VehicleDecision(long atMillis, String vehicleId, String type, String commandKind, String platformHex, String status) {
	}

	private static final Map<String, Boolean> PLATFORM_BUSY = new ConcurrentHashMap<>();

	private TscDispatchBridge() {
	}

	public static void start() {
		MtrRealRail.LOGGER.info("[{}] 已启用模组内置调度和执行桥", MtrRealRail.MOD_ID);
	}

	public static void stop() {
		clear();
	}

	public static boolean isEnabled() {
		return RealRailConfig.INSTANCE.tscDispatchEnabled;
	}

	public static boolean isRequired() {
		return RealRailConfig.INSTANCE.tscDispatchRequired;
	}

	public static boolean isOnline() {
		final EmbeddedDispatchService embedded = MtrRealRail.dispatcher();
		return isEnabled() && embedded != null && embedded.isRunning();
	}

	public static String statusText() {
		if (!isEnabled()) {
			return "RealRail 内置调度已关闭";
		}
		final EmbeddedDispatchService embedded = MtrRealRail.dispatcher();
		return embedded == null ? "RealRail 内置调度尚未启动" : embedded.stateText();
	}

	public static VehicleDecision decisionFor(String vehicleId) {
		final Service service = service();
		if (service == null || service.dispatcher() == null || vehicleId == null) {
			return null;
		}
		final long expectedId = org.tsc.dispatch.util.Ids.parse(vehicleId, Long.MIN_VALUE);
		final List<Object> decisions = service.dispatcher().recentJson(200);
		for (final Object item : decisions) {
			final Map<String, Object> decision = Json.asObject(item);
			final String id = Json.string(decision, "vehicle", "");
			if (!id.equals(vehicleId) && (expectedId == Long.MIN_VALUE || org.tsc.dispatch.util.Ids.parse(id, Long.MAX_VALUE) != expectedId)) {
				continue;
			}
			final Map<String, Object> command = Json.asObject(decision.get("command"));
			final Map<String, Object> args = Json.asObject(command.get("args"));
			final String platform = Json.string(args, "platformId", Json.string(args, "platform", ""));
			return new VehicleDecision(Json.number(decision, "at", 0), id,
				Json.string(decision, "type", ""), Json.string(command, "kind", ""), platform,
				Json.string(decision, "status", Json.string(decision, "outcome", "")));
		}
		return null;
	}

	public static VehicleDecision decisionFor(long vehicleId) {
		final String hex = Long.toHexString(vehicleId);
		return decisionForOrNull(String.valueOf(vehicleId), hex, "0x" + hex, hex.toUpperCase(Locale.ROOT));
	}

	private static VehicleDecision decisionForOrNull(String... keys) {
		for (final String key : keys) {
			final VehicleDecision decision = decisionFor(key);
			if (decision != null) {
				return decision;
			}
		}
		return null;
	}

	public static Map<String, Boolean> platformBusy() {
		return PLATFORM_BUSY;
	}

	public static int decisionCount() {
		final Service service = service();
		return service == null || service.dispatcher() == null ? 0 : service.dispatcher().recentJson(200).size();
	}

	public static boolean isBridgeOnline() {
		final EmbeddedBridge bridge = bridge();
		return bridge != null && bridge.config().enabled && bridge.isListening();
	}

	public static boolean isBridgeReady() {
		final EmbeddedBridge bridge = bridge();
		return isBridgeOnline() && bridge.host().ready();
	}

	public static String bridgeFeatures() {
		return "set_platform,deploy_train,return_to_depot,dwell_control";
	}

	public static boolean isChainActive() {
		return isOnline() && isBridgeReady();
	}

	public static String chainStatusText() {
		if (!isEnabled()) {
			return "内置调度已关闭";
		}
		return (isOnline() ? "内置调度已连接" : statusText()) + " / "
			+ (isBridgeReady() ? "执行桥已连接（" + bridgeFeatures() + "）"
				: isBridgeOnline() ? "执行桥等待 MTR 服务器数据" : "执行桥未监听端口或配置已停用");
	}

	public static void clear() {
		PLATFORM_BUSY.clear();
	}

	public static String bridgeToken() {
		final EmbeddedBridge bridge = bridge();
		if (bridge != null && !bridge.config().token.isBlank()) {
			return bridge.config().token;
		}
		return RealRailConfig.INSTANCE.bridgeToken == null ? "" : RealRailConfig.INSTANCE.bridgeToken.trim();
	}

	private static Service service() {
		final EmbeddedDispatchService embedded = MtrRealRail.dispatcher();
		return embedded == null ? null : embedded.service();
	}

	private static EmbeddedBridge bridge() {
		final EmbeddedDispatchService embedded = MtrRealRail.dispatcher();
		return embedded == null ? null : embedded.bridge();
	}
}
