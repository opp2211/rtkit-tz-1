package ru.rtkit;

import lombok.extern.slf4j.Slf4j;
import ru.rtkit.exception.NotFoundException;
import ru.rtkit.exception.NotModifiedException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

@Slf4j
public class Server {

    private static final String MAIN_PAGE = "/index.html";

    private final LFUFileManager fileManager;
    private final int port;
    private final ExecutorService threadPool;

    public Server(LFUFileManager fileManager, int port, ExecutorService threadPool) {
        this.fileManager = fileManager;
        this.port = port;
        this.threadPool = threadPool;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server listening on port {}", port);
            while (true) {
                Socket socket = serverSocket.accept();
                log.debug("Client connected!");
                threadPool.submit(() -> readAndResponse(socket));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void readAndResponse(Socket socket) {
        try (
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            LinkedList<String> requestStrings = readRequest(br);
            if (!requestStrings.isEmpty()) {
                String firstLine = requestStrings.pollFirst();
                log.info("Request: {}", firstLine);
                String[] split = Objects.requireNonNull(firstLine).split(" ");
                String method = split[0];
                String strPath = split[1];

                if (strPath.equals("/")) {
                    strPath = MAIN_PAGE;
                    log.info("Resource substituted to {}", strPath);
                }

                Map<String, String> headers = new HashMap<>();
                requestStrings.stream()
                        .map(s -> s.split(": "))
                        .forEach(h -> headers.put(h[0], h[1]));

                String modifiedSince = headers.get("If-Modified-Since");
                String noneMatch = headers.get("If-None-Match");
                try {
                    CachedFile cachedFile = fileManager.get(strPath, noneMatch, modifiedSince);
                    
                    sendln(out, "HTTP/1.1 200 OK");
                    sendln(out, "Content-Type: " + cachedFile.getContentType());
                    sendln(out, "Content-Length: " + cachedFile.getContentLength());
                    sendln(out, "Last-Modified: " + cachedFile.getLastModified());
                    sendln(out, "Etag: " + cachedFile.getEtag());
                    sendln(out);
                    send(out, cachedFile.getContent());
                    log.info("Response is 200 OK");
                } catch (NotFoundException e) {
                    sendln(out, "HTTP/1.1 404 Not Found");
                    sendln(out);
                    sendln(out, e.getMessage());
                    log.info("Response is 404 Not Found");
                }
                catch (NotModifiedException e) {
                    sendln(out, "HTTP/1.1 304 Not Modified");
                    sendln(out);
                    sendln(out, e.getMessage());
                    log.info("Response is 304 Not Modified");
                }
                out.flush();
            }
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        log.debug("Client disconnected!");
    }

    private static LinkedList<String> readRequest(BufferedReader br) throws IOException {
        LinkedList<String> requestStrings = new LinkedList<>();
        String line;
        while ((line = br.readLine()) != null && !line.isEmpty()) {
            requestStrings.add(line);
        }
        return requestStrings;
    }

    private void sendln(OutputStream out, String str) throws IOException {
        send(out, str);
        sendln(out);
    }

    private void sendln(OutputStream out) throws IOException {
        out.write(10);
    }

    private void send(OutputStream out, String str) throws IOException {
        send(out, str.getBytes());
    }

    private void send(OutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }
}
