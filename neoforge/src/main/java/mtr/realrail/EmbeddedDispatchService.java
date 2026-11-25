package mtr.realrail;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtbridge.forge.BridgeCommands;
import org.mtbridge.forge.EmbeddedBridge;
import org.tsc.dispatch.Service;
import org.tsc.dispatch.config.Config;
import org.tsc.dispatch.dispatch.Command;
import org.tsc.dispatch.dispatch.Decision;
import org.tsc.dispatch.model.Vehicle;
import org.tsc.dispatch.model.WorldSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmbeddedDispatchService implements AutoCloseable {

	private static final Logger LOGGER = LogManager.getLogger("MTR RealRail");
	private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(task -> {
		final Thread thread = new Thread(task, "mtrrealrail-command-delivery");
		thread.setDaemon(true);
		return thread;
	});
	private volatile Service service;
	private volatile EmbeddedBridge bridge;
	private volatile Path configPath;
	private volatile String state = "stopped";
	private volatile String lastError = "";

	void start(EmbeddedBridge embeddedBridge, Path stateDirectory) {
		bridge = embeddedBridge;
		configPath = FMLPaths.CONFIGDIR.get().resolve("tsc-dispatch.yml");
		if (!RealRailConfig.INSTANCE.tscDispatchEnabled) {
			state = "disabled";
			return;
		}
		try {
			if (Files.notExists(configPath)) {
				Config.writeTemplate(configPath);
				LOGGER.info("已创建内置调度配置：{}", configPath.toAbsolutePath());
			}
			final Config config = Config.load(configPath);
			config.useEmbeddedBridge();
			config.override("state.dir", stateDirectory.toAbsolutePath().toString());
			config.override("bridge.base_url", "http://127.0.0.1:" + embeddedBridge.port());
			config.override("bridge.auth_token", embeddedBridge.config().token);
			final Service started = new Service(config);
			service = started;
			started.start();
			state = "running";
			lastError = "";
			LOGGER.info("RealRail 内置调度已启动，管理页 http://{}:{}/，配置文件 {}，存档状态目录 {}",
				config.get("admin.host"), config.getInt("admin.port"), configPath.toAbsolutePath(), stateDirectory.toAbsolutePath());
		} catch (Exception exception) {
			state = "failed";
			lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			final Service failed = service;
			service = null;
			if (failed != null) {
				failed.close();
			}
			LOGGER.error("RealRail 内置调度启动失败：{}。请按错误字段修正配置文件 {}；若错误指出管理页端口占用，请修改其中的 admin.port（当前值 {}）后重启服务器。",
				lastError, configPath.toAbsolutePath(), configuredAdminPort());
		}
	}

	private static String configuredAdminPort() {
		try {
			return Config.load(FMLPaths.CONFIGDIR.get().resolve("tsc-dispatch.yml")).get("admin.port");
		} catch (Exception exception) {
			return "config/tsc-dispatch.yml 中的 admin.port";
		}
	}

	public boolean isRunning() {
		return service != null && service.snapshot() != null;
	}

	public Service service() {
		return service;
	}

	public EmbeddedBridge bridge() {
		return bridge;
	}

	public String stateText() {
		final Service current = service;
		if (current == null) {
			return "内置调度=" + state + (lastError.isEmpty() ? "" : "，原因=" + lastError)
				+ "，配置=" + configPath;
		}
		final Object serviceState = current.health().get("state");
		return "内置调度=" + serviceState + "，执行桥=" + current.health().get("bridgeOnline")
			+ "，配置=" + configPath + "，管理页=http://" + current.config().get("admin.host")
			+ ":" + current.config().getInt("admin.port") + "/"
			+ (lastError.isEmpty() ? "" : "，最近命令=" + lastError);
	}

	void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tscdispatch")
			.executes(context -> status(context.getSource()))
			.then(Commands.literal("status").executes(context -> status(context.getSource()))));
	}

	private int status(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("RealRail " + stateText()), false);
		return isRunning() ? 1 : 0;
	}

	public synchronized boolean enqueue(String kind, String vehicleId, String target, String reason) {
		final Service current = service;
		if (current == null || current.primary() == null || current.snapshot() == null) {
			lastError = "内置调度尚未取得列车状态；请检查 /mtbridge status 并等待调度状态更新";
			return false;
		}
		commandExecutor.execute(() -> deliver(current, kind, vehicleId, target, reason));
		return true;
	}

	private void deliver(Service current, String kind, String vehicleId, String target, String reason) {
		final var runtime = current.primary();
		final WorldSnapshot snapshot = runtime == null ? null : runtime.snapshot();
		if (snapshot == null) {
			reject(kind, vehicleId, "尚未取得最新列车状态");
			return;
		}
		final String normalizedVehicleId = org.tsc.dispatch.util.Ids.normalize(vehicleId);
		final Vehicle vehicle = snapshot.vehicle(normalizedVehicleId);
		if (vehicle == null) {
			reject(kind, vehicleId, "TSC 状态中没有该车辆");
			return;
		}
		final Command command = new Command();
		command.kind = Command.Kind.fromWire(kind);
		if (command.kind == null) {
			reject(kind, vehicleId, "未知调度指令");
			return;
		}
		command.vehicleId = normalizedVehicleId;
		if (command.kind == Command.Kind.SET_PLATFORM || command.kind == Command.Kind.DEPLOY) {
			command.args.put("platformId", target == null || target.isBlank() ? "" : org.tsc.dispatch.util.Ids.normalize(target));
		} else if (command.kind == Command.Kind.RETURN_TO_DEPOT) {
			command.args.put("sidingId", "");
		}
		command.reason = reason;
		command.preconditionVehicleId = normalizedVehicleId;
		command.preconditionPlatformHex = vehicle.platformHex;
		command.preconditionObservedAt = snapshot.fetchedAtMillis;
		command.expiresAtMillis = System.currentTimeMillis()
			+ current.config().getLong("dispatch.command_ttl_seconds") * 1000L;
		command.maxAttempts = Math.max(1, current.config().getInt("dispatch.max_attempts"));
		command.idempotencyKey = kind + "|" + normalizedVehicleId + "|" + System.currentTimeMillis() / 60_000L;
		final Decision decision = new Decision(System.currentTimeMillis(), "MANUAL", normalizedVehicleId, reason, true, command);
		runtime.bus().submit(decision, snapshot);
		lastError = decision.status + (decision.outcome.isEmpty() ? "" : "：" + decision.outcome);
		LOGGER.info("RealRail 内置调度命令 {} → 车辆 {}：{}，结果 {} {}",
			kind, vehicleId, reason, decision.status, decision.outcome);
	}

	private void reject(String kind, String vehicleId, String reason) {
		lastError = reason;
		LOGGER.warn("RealRail 内置调度未执行命令 {} → 车辆 {}：{}。请确认车辆仍被 MTR 识别，并检查 /mtbridge status 和内置调度状态。",
			kind, vehicleId, reason);
	}

	@Override
	public synchronized void close() {
		state = "stopping";
		commandExecutor.shutdownNow();
		final Service current = service;
		service = null;
		if (current != null) {
			current.close();
		}
		bridge = null;
		state = "stopped";
	}
}
