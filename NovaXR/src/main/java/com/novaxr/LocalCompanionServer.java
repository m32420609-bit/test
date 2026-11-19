package com.novaxr;

import com.sun.net.httpserver.HttpServer;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class LocalCompanionServer {
    private final int httpPort, wsPort;
    private final Consumer<String> logger;
    private HttpServer http;
    private WebSocketServer ws;
    private ScheduledExecutorService capture;
    private volatile byte[] latestJpeg;

    public LocalCompanionServer(int httpPort, int wsPort, Consumer<String> logger) {
        this.httpPort = httpPort; this.wsPort = wsPort; this.logger = logger;
    }
    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress("0.0.0.0", httpPort), 0);
        http.createContext("/", x -> {
            byte[] page = resource("/phone/index.html");
            x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            x.sendResponseHeaders(200, page.length); try (OutputStream o = x.getResponseBody()) { o.write(page); }
        });
        http.createContext("/stream", x -> {
            x.getResponseHeaders().set("Content-Type", "multipart/x-mixed-replace; boundary=frame");
            x.getResponseHeaders().set("Cache-Control", "no-cache");
            x.sendResponseHeaders(200, 0);
            try (OutputStream out = x.getResponseBody()) {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] frame = latestJpeg;
                    if (frame != null) {
                        out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".getBytes(StandardCharsets.US_ASCII));
                        out.write(Integer.toString(frame.length).getBytes(StandardCharsets.US_ASCII));
                        out.write("\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); out.write(frame); out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                    }
                    Thread.sleep(80);
                }
            } catch (Exception ignored) {}
        });
        http.setExecutor(Executors.newCachedThreadPool()); http.start();
        ws = new WebSocketServer(new InetSocketAddress("0.0.0.0", wsPort)) {
            @Override public void onOpen(WebSocket c, ClientHandshake h) { logger.accept("Phone connected: " + c.getRemoteSocketAddress()); }
            @Override public void onClose(WebSocket c, int code, String reason, boolean remote) { logger.accept("Phone disconnected."); }
            @Override public void onMessage(WebSocket c, String message) { logger.accept("Phone telemetry: " + message.substring(0, Math.min(180, message.length()))); }
            @Override public void onError(WebSocket c, Exception ex) { logger.accept("WebSocket: " + ex.getMessage()); }
            @Override public void onStart() { logger.accept("Orientation WebSocket ready on " + wsPort); }
        };
        ws.start();
        startCapture();
    }
    private void startCapture() {
        capture = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "novaxr-capture"); t.setDaemon(true); return t; });
        capture.scheduleAtFixedRate(() -> {
            try {
                if (GraphicsEnvironment.isHeadless()) return;
                Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                BufferedImage image = new Robot().createScreenCapture(bounds);
                BufferedImage scaled = new BufferedImage(Math.min(1280, image.getWidth()), Math.max(1, image.getHeight() * Math.min(1280, image.getWidth()) / image.getWidth()), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics(); g.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null); g.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(scaled, "jpg", out); latestJpeg = out.toByteArray();
            } catch (Exception e) { logger.accept("Screen capture: " + e.getMessage()); }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    private byte[] resource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) return "<h1>NovaXR phone page missing</h1>".getBytes(StandardCharsets.UTF_8);
            return in.readAllBytes();
        }
    }
    public void stop() {
        if (capture != null) capture.shutdownNow();
        if (ws != null) { try { ws.stop(500); } catch (Exception ignored) {} }
        if (http != null) http.stop(0);
    }
}
