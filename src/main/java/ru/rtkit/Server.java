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

    private final FileManager fileManager;
    private final int port;
    private final ExecutorService threadPool;

    public Server(FileManager fileManager, int port, ExecutorService threadPool) {
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
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void readAndResponse(Socket socket) {
        try (
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = socket.getOutputStream();
                PrintWriter pw = new PrintWriter(out)
        ) {
            //считываем все, что было отправлено клиентом
            LinkedList<String> requestStrings = new LinkedList<>();
            String line;
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                requestStrings.add(line);
            }
            if (!requestStrings.isEmpty()) {
                String firstLine = requestStrings.pollFirst();
                log.info("Request: {}", firstLine);
                String[] split = Objects.requireNonNull(firstLine).split(" ");
                String method = split[0];
                String strPath = split[1];

                if (strPath.equals("/")) {
                    strPath = "/index.html";
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

                    pw.println("HTTP/1.1 200 OK");
                    pw.println("Content-Type: " + cachedFile.getContentType());
                    pw.println("Content-Length: " + cachedFile.getContentLength());
                    pw.println("Last-Modified: " + cachedFile.getLastModified());
                    pw.println("Etag: " + cachedFile.getEtag());
                    pw.println();
                    pw.println(new String(cachedFile.getContent()));
                    log.info("Response is 200 OK");
                } catch (NotFoundException e) {
                    pw.println("HTTP/1.1 404 Not Found");
                    pw.println();
                    pw.println(e.getMessage());
                    log.info("Response is 404 Not Found");
                }
                catch (NotModifiedException e) {
                    pw.println("HTTP/1.1 304 Not Modified");
                    pw.println();
                    pw.println(e.getMessage());
                    log.info("Response is 304 Not Modified");
                }
                out.flush();
            }
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
        }
        log.debug("Client disconnected!");
    }
}
