package net.TrxaXe.chatforward.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.logging.Logger;

import static net.TrxaXe.chatforward.client.ChatMessageServer.*;

public class ChatforwardClient implements ClientModInitializer {
    public static final Logger logger = Logger.getLogger("ChatForward");
    public static ClientPlayerEntity player;
    public static int port = 8081;
    @Override
    public void onInitializeClient() {
        MinecraftClient Client = MinecraftClient.getInstance();
        player = Client.player;

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (running.get()) {
                addMessage(message.getString());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralCommandNode<FabricClientCommandSource> ChatforwardNode = ClientCommandManager
                    .literal("Chatforward")
                    .executes(context -> {
                        String Feedback;
                        if (running.get()) {
                            stopServer();
                            Feedback = "Forward stopped";
                        } else {
                            startServer();
                            Feedback = "Forward started at"+ port;
                        }
                        context.getSource().sendFeedback(Text.literal(Feedback));
                        return 1;
                    })
                    .build();

            LiteralCommandNode<FabricClientCommandSource> StatusNode = ClientCommandManager
                    .literal("status")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal(
                                "Chat Forward is " + (!running.get() ? "stop" : ("running at port: " + port)))
                        );
                        return 1;
                    })
                    .build();

            LiteralCommandNode<FabricClientCommandSource> PortNode = ClientCommandManager
                    .literal("port")
                    .then(ClientCommandManager.argument("Port", IntegerArgumentType.integer(0,65536)))
                    .executes(context -> {
                        port = IntegerArgumentType.getInteger(context, "Port");
                        context.getSource().sendFeedback(Text.literal("Now port is:" + port));
                        return 1;
                    })
                    .build();
            ChatforwardNode.addChild(StatusNode);
            ChatforwardNode.addChild(PortNode);
            dispatcher.getRoot().addChild(ChatforwardNode);
        });
    }
    public static void sendMessage(String string) {
        player.sendMessage(Text.literal(string),false);
    }
}
