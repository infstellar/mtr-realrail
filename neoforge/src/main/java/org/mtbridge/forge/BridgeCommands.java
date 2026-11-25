package org.mtbridge.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.mtr.core.simulation.Simulator;
import org.mtbridge.api.BridgeResult;
import org.mtbridge.core.BridgeCommand;
import org.mtbridge.core.BridgeService;
import org.mtbridge.core.Json;

import java.util.function.Supplier;

/** {@code /mtbridge status|deploy|set_platform|return_depot}. */
public final class BridgeCommands {

	private BridgeCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<EmbeddedBridge> current) {
		dispatcher.register(Commands.literal("mtbridge")
			.requires(source -> source.hasPermission(2))
			.executes(context -> withBridge(context.getSource(), current, BridgeCommands::status))
			.then(Commands.literal("status").executes(context -> withBridge(context.getSource(), current, BridgeCommands::status)))
			.then(Commands.literal("deploy")
				.then(Commands.argument("train", StringArgumentType.word())
					.then(Commands.argument("platform", StringArgumentType.word())
						.executes(context -> withBridge(context.getSource(), current, (source, service, host) ->
							run(source, service, host, BridgeCommand.Kind.DEPLOY,
								StringArgumentType.getString(context, "train"), StringArgumentType.getString(context, "platform"), 0))))))
			.then(Commands.literal("set_platform")
				.then(Commands.argument("train", StringArgumentType.word())
					.then(Commands.argument("platform", StringArgumentType.word())
						.executes(context -> withBridge(context.getSource(), current, (source, service, host) ->
							run(source, service, host, BridgeCommand.Kind.SET_PLATFORM,
								StringArgumentType.getString(context, "train"), StringArgumentType.getString(context, "platform"), 0))))))
			.then(Commands.literal("return_depot")
				.then(Commands.argument("train", StringArgumentType.word())
					.executes(context -> withBridge(context.getSource(), current, (source, service, host) ->
						run(source, service, host, BridgeCommand.Kind.RETURN_TO_DEPOT, StringArgumentType.getString(context, "train"), "", 0)))
					.then(Commands.argument("siding", StringArgumentType.word())
						.executes(context -> withBridge(context.getSource(), current, (source, service, host) ->
							run(source, service, host, BridgeCommand.Kind.RETURN_TO_DEPOT,
								StringArgumentType.getString(context, "train"), StringArgumentType.getString(context, "siding"), 0))))))
			.then(Commands.literal("recover")
				.executes(context -> withBridge(context.getSource(), current, BridgeCommands::recover)))
			.then(Commands.literal("audit")
				.executes(context -> withBridge(context.getSource(), current, BridgeCommands::audit)))
			.then(Commands.literal("dwell")
				.then(Commands.argument("train", StringArgumentType.word())
					.then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1))
						.executes(context -> withBridge(context.getSource(), current, (source, service, host) ->
							run(source, service, host, BridgeCommand.Kind.DWELL, StringArgumentType.getString(context, "train"), "",
								com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds"))))))));
	}

	private interface Action {
		int run(CommandSourceStack source, BridgeService service, ForgeHost host);
	}

	private static int withBridge(CommandSourceStack source, Supplier<EmbeddedBridge> current, Action action) {
		final EmbeddedBridge bridge = current.get();
		if (bridge == null || !bridge.config().enabled) {
			source.sendFailure(Component.literal("RealRail 执行桥尚未启用。请检查服务器的 config/mtbridge.json 和启动日志。"));
			return 0;
		}
		return action.run(source, bridge.service(), bridge.host());
	}

	/** Repairs every depot whose sidings lost their outbound paths. */
	private static int recover(CommandSourceStack source, BridgeService service, ForgeHost host) {
		final java.util.List<Simulator> simulators = simulators(host);
		if (simulators.isEmpty()) {
			source.sendFailure(Component.literal("bridge has no simulator yet"));
			return 0;
		}
		final int recovered = service.recoverDepots(simulators, true);
		// Publish immediately: the depot is now in its recovery window, so the advisory drops it
		// in the same breath instead of waiting for the next periodic audit.
		service.auditAll(simulators);
		source.sendSuccess(() -> Component.literal("recovered " + recovered + " depot(s)"), false);
		return recovered > 0 ? 1 : 0;
	}

	private static int audit(CommandSourceStack source, BridgeService service, ForgeHost host) {
		final java.util.List<Simulator> simulators = simulators(host);
		if (simulators.isEmpty()) {
			source.sendFailure(Component.literal("bridge has no simulator yet"));
			return 0;
		}
		final java.util.List<java.util.Map<String, Object>> broken = service.auditAll(simulators);
		// JSON rather than Java's map toString, so the output can be logged and parsed too.
		final String text = broken.isEmpty()
			? "all depots have paths"
			: org.mtbridge.core.Json.write(broken);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/** Every loaded dimension: depot health is not a property of dimension 0. */
	private static java.util.List<Simulator> simulators(ForgeHost host) {
		final java.util.List<Simulator> result = new java.util.ArrayList<>();
		for (int dimension = 0; dimension < host.dimensionCount(); dimension++) {
			final Simulator simulator = host.simulator(dimension);
			if (simulator != null) {
				result.add(simulator);
			}
		}
		return result;
	}

	private static int status(CommandSourceStack source, BridgeService service, ForgeHost host) {
		final String text = "bridge=" + (host.ready() ? "ready" : "unavailable")
			+ " locator=" + host.locator()
			+ " attempts=" + host.locateAttempts()
			+ " dims=" + host.dimensionCount()
			+ (host.ready() ? " ids=" + host.dimensionIds() : "")
			+ " executed=" + service.executed()
			+ " deferred=" + service.deferred()
			+ " failed=" + service.failed()
			+ " pending=" + service.pendingIntents()
			+ " autoReleased=" + service.autoReleased();
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int run(CommandSourceStack source, BridgeService service, ForgeHost host, BridgeCommand.Kind kind,
						   String train, String target, int seconds) {
		final Simulator simulator = host.simulator(0);
		if (simulator == null) {
			source.sendFailure(Component.literal("bridge has no simulator yet"));
			return 0;
		}
		final BridgeResult result = service.execute(simulator, BridgeCommand.of(kind, train, target, seconds), BridgeService.Source.COMMAND);
		final String text = kind.wire + " -> " + result.outcome() + " " + result.message();
		if (result.success()) {
			source.sendSuccess(() -> Component.literal(text), false);
		} else {
			source.sendFailure(Component.literal(text + " " + Json.write(result.toJson())));
		}
		return result.success() ? 1 : 0;
	}
}
