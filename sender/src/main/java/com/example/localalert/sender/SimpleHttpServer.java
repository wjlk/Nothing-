package com.example.localalert.sender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimpleHttpServer {
    public interface RequestListener {
        void onRequest(String method, String path);
    }

    private final int port;
    private final RequestListener listener;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public SimpleHttpServer(int port, RequestListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(port);
        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    clients.execute(() -> handle(socket));
                } catch (IOException error) {
                    if (running) {
                        // Continue accepting after a transient socket failure.
                    }
                }
            }
        }, "sender-http-acceptor");
        acceptThread.start();
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream output = client.getOutputStream()) {
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ", 3);
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "";

            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0
                        && "content-length".equalsIgnoreCase(line.substring(0, separator).trim())) {
                    try {
                        contentLength = Integer.parseInt(line.substring(separator + 1).trim());
                    } catch (NumberFormatException ignored) {
                        contentLength = 0;
                    }
                }
            }
            while (contentLength-- > 0 && reader.read() != -1) {
                // Consume the optional request body before responding.
            }

            if ("/ack".equals(path)) {
                listener.onRequest(method, path);
                writeResponse(output, 200, "تم الرد");
            } else {
                writeResponse(output, 404, "Not Found");
            }
        } catch (IOException ignored) {
            // A disconnected local client should not stop the foreground service.
        }
    }

    private void writeResponse(OutputStream output, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found")
                + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                + payload.length + "\r\nConnection: close\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
        output.flush();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        clients.shutdownNow();
    }
}