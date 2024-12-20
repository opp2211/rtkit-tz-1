package ru.rtkit;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static String configFile = "server.properties";

    public static void main(String[] args) {
        CommandLine cmd = parseCL(args);
        if (cmd.hasOption("c")) {
            setConfigFile(cmd.getOptionValue("c"));
        }

        Properties props = new Properties();
        loadProperties(props);

        String rootDir = props.getProperty("root_dir");
        long cacheSize = HumanBytesFormatter.toBytes(props.getProperty("cache_size"));
        int port = Integer.parseInt(props.getProperty("port"));

        LFUFileManager fileManager = new LFUFileManager(rootDir, cacheSize);

        ExecutorService executorService = Executors.newCachedThreadPool();
        Server server = new Server(fileManager, port, executorService);
        server.start();
    }

    private static CommandLine parseCL(String[] args) {
        Options options = new Options();
        options.addOption("c", "config", true, "config file");

        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    private static void setConfigFile(String newConfig) {
        if (newConfig.endsWith(".properties")) {
            configFile = newConfig;
        } else {
            System.err.printf("Config file %s should has .properties extension%n", newConfig);
            System.exit(1);
        }
    }

    private static void loadProperties(Properties props) {
        try (InputStream is = ClassLoader.getSystemResourceAsStream(configFile)) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.printf("Config file %s not found%n", configFile);
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}