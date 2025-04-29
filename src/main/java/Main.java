import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class Main {
    private static final int PORT = 4221;
    private static final String ECHO_PATH = "/echo/";
    private static String directory;
    private static byte[] compressedBody = new byte[0];

    public static void main(String[] args) {
        if (args.length > 1 && args[0].equals("--directory")) {
            directory = args[1];
        }

        System.out.println("Server starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted connection");
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (
                socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream()
        ) {
            while (true) {
                HttpRequest request = HttpRequest.parse(in);
                if (request == null) break;

                boolean keepAlive = !"close".equalsIgnoreCase(request.headers.getOrDefault("connection", "keep-alive"));

                String response = buildResponse(request, keepAlive);
                out.write(response.getBytes());

                if (compressedBody.length > 0) {
                    out.write(compressedBody);
                    compressedBody = new byte[0];
                }

                out.flush();

                if (!keepAlive) break;
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static String buildResponse(HttpRequest request, boolean keepAlive) {
        StringBuilder response = new StringBuilder();
        String path = request.path;

        if ("POST".equals(request.method)) {
            if (path.startsWith("/files/")) {
                File file = new File(directory + Path.of(path.substring(7)));
                try {
                    if (file.createNewFile()) {
                        try (FileWriter fw = new FileWriter(file)) {
                            fw.write(request.body);
                        }
                        response.append("HTTP/1.1 201 Created\r\n");
                    } else {
                        response.append("HTTP/1.1 404 Not Found\r\n");
                    }
                } catch (IOException e) {
                    System.err.println("Exception writing file: " + e);
                    response.append("HTTP/1.1 500 Internal Server Error\r\n");
                }
            }
        } else {
            if ("/".equals(path)) {
                response.append("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n");
            } else if (path.startsWith(ECHO_PATH)) {
                String body = path.substring(ECHO_PATH.length());
                if (request.encoding != null && request.encoding.contains("gzip")) {
                    compressedBody = compressGzip(body);
                    response.append("HTTP/1.1 200 OK\r\n")
                            .append("Content-Type: text/plain\r\n")
                            .append("Content-Encoding: gzip\r\n")
                            .append("Content-Length: ").append(compressedBody.length).append("\r\n");
                } else {
                    response.append("HTTP/1.1 200 OK\r\n")
                            .append("Content-Type: text/plain\r\n")
                            .append("Content-Length: ").append(body.length()).append("\r\n\r\n")
                            .append(body);
                }
            } else if ("/user-agent".equals(path)) {
                String agent = request.headers.getOrDefault("user-agent", "");
                response.append("HTTP/1.1 200 OK\r\n")
                        .append("Content-Type: text/plain\r\n")
                        .append("Content-Length: ").append(agent.length()).append("\r\n\r\n")
                        .append(agent);
            } else if (path.startsWith("/files/")) {
                Path filepath = Path.of(directory + path.substring(7));
                if (Files.exists(filepath)) {
                    try {
                        String content = Files.readString(filepath);
                        response.append("HTTP/1.1 200 OK\r\n")
                                .append("Content-Type: application/octet-stream\r\n")
                                .append("Content-Length: ").append(content.length()).append("\r\n\r\n")
                                .append(content);
                    } catch (IOException e) {
                        System.err.println("Exception reading file: " + e);
                        response.append("HTTP/1.1 500 Internal Server Error\r\n\r\n");
                    }
                } else {
                    response.append("HTTP/1.1 404 Not Found\r\n\r\n");
                }
            } else {
                response.append("HTTP/1.1 404 Not Found\r\n\r\n");
            }
        }

        // Add Connection header
        if (!response.toString().contains("Connection:")) {
            response.insert(response.indexOf("\r\n") + 2, "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n");
        }

        // Ensure headers end properly
        if (!response.toString().contains("\r\n\r\n")) {
            response.append("\r\n");
        }

        return response.toString();
    }

    private static byte[] compressGzip(String str) {
        if (str == null || str.isEmpty()) return new byte[0];

        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(str.getBytes(StandardCharsets.UTF_8));
            gzipStream.finish(); // Ensures complete gzip format
            return byteStream.toByteArray();
        } catch (IOException e) {
            System.err.println("Gzip compression failed: " + e);
            return new byte[0];
        }
    }
}

// Helper class for parsing HTTP requests
class HttpRequest {
    public String method;
    public String path;
    public String version;
    public Map<String, String> headers = new HashMap<>();
    public String body;
    public List<String> encoding;

    public static HttpRequest parse(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) return null;

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) return null;

        HttpRequest request = new HttpRequest();
        request.method = parts[0];
        request.path = parts[1];
        request.version = parts[2];

        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                if (key.equals("accept-encoding")) {
                    request.encoding = Arrays.asList(value.split(",\\s*"));
                } else {
                    request.headers.put(key, value);
                }
            }
        }

        int contentLength = Integer.parseInt(request.headers.getOrDefault("content-length", "0"));
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            in.read(bodyChars, 0, contentLength);
            request.body = new String(bodyChars);
        }

        return request;
    }
}
