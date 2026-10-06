package com.edges.mask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

final class RootShell {
    static final class Result {
        final int code; final String output;
        Result(int c, String o) { code = c; output = o; }
        String checked() throws IOException {
            if (code != 0) throw new IOException("Root 命令失败或授权被拒绝（" + code + "）");
            return output;
        }
    }
    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    static Result run(String command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] b = new byte[2048]; int n;
                while ((n = in.read(b)) != -1) {
                    synchronized (bytes) { if (bytes.size() + n <= 65536) bytes.write(b, 0, n); }
                }
            } catch (IOException ignored) { }
        }, "root-output");
        reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(300, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                throw new IOException("Root 命令超时，已取消");
            }
            reader.join(1000);
            synchronized (bytes) { return new Result(process.exitValue(), bytes.toString("UTF-8")); }
        } finally { process.getOutputStream().close(); }
    }
}
