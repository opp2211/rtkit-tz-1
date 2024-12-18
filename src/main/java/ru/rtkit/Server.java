package ru.rtkit;

import lombok.extern.slf4j.Slf4j;
import ru.rtkit.exception.NotFoundException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
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
                threadPool.submit(() -> method(socket));
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void method(Socket socket) {
        try (
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter output = new PrintWriter(socket.getOutputStream())
        ) {
            //считываем все, что было отправлено клиентом
            LinkedList<String> requestStrings = new LinkedList<>();
            String line;
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                requestStrings.add(line);
            }
            if (!requestStrings.isEmpty()) {
                String[] split = Objects.requireNonNull(requestStrings.pollFirst()).split(" ");
                String resource = split[1];

                if (resource.equals("/")) {
                    resource = "/index.html";
                }
                try {
                    CachedFile cachedFile = fileManager.get(resource);

                    // отправляем ответ
                    output.println("HTTP/1.1 200 OK");
                    output.println("Content-Type: " + cachedFile.getContentType());
                    output.println("Content-Length: " + cachedFile.getContentLength());
                    output.println("Last-Modified: " + cachedFile.getLastModified());
                    output.println("Etag: " + cachedFile.getEtag());
                    output.println();
                    output.println(cachedFile.getContent());
                } catch (NotFoundException e) {
                    output.println("HTTP/1.1 404 Not Found");
                    output.println();
                    output.println(e.getMessage());
                }
                output.flush();
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("Client disconnected!");
    }
}
