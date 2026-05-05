package dev.gustavoavila.websocketclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class TestServerManager {

    static final int PORT = 8765;

    private static Process process;

    private static String resolveNodeExecutable() {
        // 1. Plain PATH (works in CI or when nvm is sourced in the shell)
        if (probe("node")) return "node";

        // 2. Scan ~/.nvm/versions/node/ for any installed version
        java.io.File nvmVersionsDir = new java.io.File(System.getProperty("user.home"), ".nvm/versions/node");
        if (nvmVersionsDir.isDirectory()) {
            java.io.File[] versions = nvmVersionsDir.listFiles(java.io.File::isDirectory);
            if (versions != null) {
                for (java.io.File v : versions) {
                    String candidate = new java.io.File(v, "bin/node").getAbsolutePath();
                    if (probe(candidate)) return candidate;
                }
            }
        }

        // 3. Common system paths
        for (String p : new String[]{"/usr/bin/node", "/usr/local/bin/node"}) {
            if (probe(p)) return p;
        }

        throw new IllegalStateException("Cannot find 'node' executable. Install Node.js and ensure it is on PATH.");
    }

    private static boolean probe(String cmd) {
        try {
            return new ProcessBuilder(cmd, "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void start() throws Exception {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path serverDir = projectRoot.resolveSibling("java-android-websocket-client-testserver");

        ProcessBuilder pb = new ProcessBuilder(resolveNodeExecutable(), "server.js")
                .directory(serverDir.toFile())
                .redirectErrorStream(true);

        process = pb.start();

        // Wait for the READY signal (max 10 seconds)
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        long deadline = System.currentTimeMillis() + 10_000;
        String line;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Node server exited unexpectedly during startup");
            }
            if (reader.ready() && (line = reader.readLine()) != null) {
                if (line.contains("READY")) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        process.destroy();
        throw new IllegalStateException("Timed out waiting for WebSocket test server to start");
    }

    public static void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String wsUrl(String path) {
        return "ws://localhost:" + PORT + path;
    }
}
