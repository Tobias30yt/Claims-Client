package de.tobias30.claims.client;

import de.tobias30.claims.shared.Wire;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.client.KeyMapping;
import net.fabricmc.loader.api.FabricLoader;

public final class ClaimsClient implements ClientModInitializer {
    private boolean announced;
    private boolean snakeAnnounced;
    private static KeyMapping openGui;
    static long currentSnakeSession = Long.MIN_VALUE;
    static SnakeScreen activeSnake;
    @Override public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer("claims_client").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
        new ClientUpdateChecker(version).start();
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Hello.TYPE, Payloads.Hello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Action.TYPE, Payloads.Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Menu.TYPE, Payloads.Menu.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.SnakeHello.TYPE, Payloads.SnakeHello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.SnakeAction.TYPE, Payloads.SnakeAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Open.TYPE, Payloads.Open.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Snake.TYPE, Payloads.Snake.CODEC);
        KeyMapping.Category category = KeyMapping.Category.register(net.minecraft.resources.Identifier.parse("claims:general"));
        openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.claims.open_gui", 79, category));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Menu.TYPE, (payload, context) -> {
            try {
                Wire.Menu menu = Wire.menu(payload.bytes());
                context.client().execute(() -> context.client().setScreenAndShow(new ClaimsScreen(menu)));
            } catch (IOException e) {
                System.getLogger("Claims Client").log(System.Logger.Level.WARNING, "Invalid Claims menu; use /claims chest for fallback.", e);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Snake.TYPE, (payload, context) -> context.client().execute(() -> {
            ByteBuffer b = ByteBuffer.wrap(payload.bytes());
            if (b.remaining() != 13 || b.getInt() != 0x534E414B) return;
            byte op = b.get(); long session = b.getLong();
            if (op == 0) { currentSnakeSession = session; activeSnake = new SnakeScreen(session); context.client().setScreenAndShow(activeSnake); }
            else if (op == 1 && currentSnakeSession == session) {
                if (activeSnake != null) activeSnake.onClose();
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { announced = false; snakeAnnounced = false; });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { announced = false; snakeAnnounced = false; });
        // Paper advertises plugin channels after joining. Wait until registration arrives.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!announced && client.getConnection() != null && ClientPlayNetworking.canSend(Payloads.Hello.TYPE) && ClientPlayNetworking.canSend(Payloads.Action.TYPE)) {
                ClientPlayNetworking.send(new Payloads.Hello(ByteBuffer.allocate(4).putInt(Wire.VERSION).array())); announced = true;
            }
            if (!snakeAnnounced && client.getConnection() != null && ClientPlayNetworking.canSend(Payloads.SnakeHello.TYPE)) {
                ClientPlayNetworking.send(new Payloads.SnakeHello(ByteBuffer.allocate(4).putInt(0x534E414B).array())); snakeAnnounced = true;
            }
            if (openGui != null && openGui.consumeClick() && client.getConnection() != null && ClientPlayNetworking.canSend(Payloads.Open.TYPE))
                ClientPlayNetworking.send(new Payloads.Open(new byte[0]));
        });
    }
}
