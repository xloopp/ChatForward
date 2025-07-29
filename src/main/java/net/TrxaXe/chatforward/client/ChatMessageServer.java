package net.TrxaXe.chatforward.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.TrxaXe.chatforward.client.ChatforwardClient.logger;

public class ChatMessageServer {
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static ServerSocket serverSocket;
    public static final AtomicBoolean running = new AtomicBoolean(false);
    private static final Object lock = new Object(); // 用于同步 serverSocket 操作

    public static void startServer() {
        if (running.get()) return; // 防止重复启动

        int port = ChatforwardClient.port;
        running.set(true);

        new Thread(() -> {
            try {
                synchronized (lock) {
                    serverSocket = new ServerSocket(port);
                    serverSocket.setSoTimeout(1000); // 每隔 1s 检查 running 状态
                    logger.info("Chat API Server started on 127.0.0.1:" + port);
                }

                while (running.get()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(5000); // 设置客户端超时
                        new Thread(() -> handleClient(clientSocket)).start();
                    } catch (SocketTimeoutException e) {
                        // 超时检查 running 状态
                    } catch (IOException e) {
                        if (running.get()) logger.warning("ChatMessageServer Error: " + e);
                    }
                }
            } catch (IOException e) {
                logger.warning("ChatMessageServer Error: " + e);
            } finally {
                synchronized (lock) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            logger.warning("Failed to close server socket: " + e);
                        }
                    }
                }
            }
        }).start();
    }

    private static void handleClient(Socket clientSocket) {
        try (clientSocket;
             PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            try {
                clientSocket.setSoTimeout(5000); // 设置读取命令超时

                // 1. 发送全部历史消息
                for (String pastMessage : messageHistory) {
                    out.println(pastMessage);
                    clientSocket.setSoTimeout(3000);
                    String ack = in.readLine();
                    if (ack == null || !ack.equals("ACK")) {
                        logger.warning("客户端未确认消息: " + pastMessage);
                        break; // 断开连接
                    }
                }

                // 2. 监听新消息
                while (running.get() && !clientSocket.isClosed()) {
                    String message = messageQueue.take();
                    out.println(message);
                    clientSocket.setSoTimeout(3000);
                    String ack = in.readLine();
                    if (ack == null) {
                        logger.warning("客户端未确认消息: " + message);
                        break; // 断开连接
                    } else {
                        logger.info(ack);
                        if (ack.startsWith("SDME")) {
                            String messageToSend = ack.substring(5);
                            try {
                                // 调用sendMessage方法
                                ChatforwardClient.sendMessage(messageToSend);
                                out.println("ACK"); // 成功确认
                                logger.info("Processed SDME command with message: " + messageToSend);
                            } catch (Exception e) {
                                out.println("ERROR: Failed to send message - " + e.getMessage()); // 错误响应
                                logger.warning("Failed to process SDME command: " + e);
                            }
                        } else {
                            out.println("ERROR: Unsupported command");
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                logger.warning("等待ACK超时");
            } catch (Exception e) {
                logger.warning("Client handler error: " + e);
            }
        } catch (IOException e) {
            logger.warning("关闭连接失败: " + e);
        }
    }

    public static void addMessage(String message) {
        if (running.get()) {
            logger.info("[转发消息] " + message);
            messageHistory.add(message);
            boolean success = messageQueue.offer(message); // 检查返回值
            if (!success) {
                logger.warning("Failed to add message to queue (queue full?)");
            }
        }
    }


    public static void stopServer() {
        if (!running.getAndSet(false)) return; // 已经关闭

        synchronized (lock) {
            if (serverSocket != null) {
                try {
                    serverSocket.close(); // 强制中断 accept()
                } catch (IOException e) {
                    logger.warning("Failed to close server socket: " + e);
                }
            }
        }
        messageQueue.offer(""); // 唤醒所有阻塞的 poll()
    }
}