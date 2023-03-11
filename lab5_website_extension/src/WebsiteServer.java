import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.pbkdf2.Integers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import static java.nio.file.Files.readAllBytes;

public class WebsiteServer {
    HttpServer server;
    private static final int HTTP_RESPONSE_SUCCESS = 200;
    private static final int HTTP_ACCESS_DENIED = 401;

    private static final String MAIN_PAGE_PATH_STRING = "/main_page";
    private static final String GET_DATA_PATH_STRING = "/main_page/data/";
    private static final String GET_NEXT_CHUNK_PATH_STRING = "/get_next_chunk";
    private static final String GET_PREV_CHUNK_PATH_STRING = "/get_prev_chunk";
    private static final String GET_MAX_INDEX_PATH_STRING = "/get_max_index";

    private static Connection databaseConnection;
    private static boolean isDatabaseWork = false;
    private static final String databaseName = "test";
    private static final String databaseHost = "localhost:5432";
    private static final String databaseUser = "postgres";
    private static final String databaseUserPassword = "51625162";

    private static final int chunkSize = 100;

    private static final Gson parser = new Gson();

    private static class MainPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("!!!");
            String file = new String(Files.readAllBytes(Paths.get("/home/kotbegemot/Desktop/Labs/NetworkTechnology/lab5_website_extension/src/Frontend/Website.html")));
            sendFile(exchange, file);
        }

        private void sendFile(HttpExchange exchange, String file) {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            try {
                exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, file.length());
                OutputStream stream = exchange.getResponseBody();
                stream.write(file.getBytes(StandardCharsets.UTF_8));
                stream.close();
            } catch (IOException e) {
                System.err.println("ERROR. Message can't be send to client. Client: " + exchange.getRemoteAddress().toString()
                        + " Error message: " + e.getMessage());
                try {
                    exchange.getResponseBody().close();
                } catch (IOException ee) {

                }
                return;
            }
        }
    }

    private static class GetMaxIndexHandler implements HttpHandler {
        private class MaxIndex// JSON CONVERSION CLASS
        {
            MaxIndex(int index) {
                maxIndex = index;
            }

            private int maxIndex;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int maxIndex = -1;
            try {
                maxIndex = getMaxIndexInDatabase();
            } catch (SQLException e) {
                System.err.println("ERROR. Server can't read data from database. Max int will be send as error value -1" +
                        e.getMessage());
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            byte[] data = parser.toJson(new MaxIndex(maxIndex)).getBytes();
            exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, data.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(data);
            stream.close();
        }
    }

    private static int getMaxIndexInDatabase() throws SQLException {
        Statement statement = databaseConnection.createStatement();
        ResultSet set = statement.executeQuery("SELECT max(row_id) AS MAX FROM logs;");
        set.next();
        return set.getInt("MAX");
    }

    private static String[] getMainPageStringsInDatabase(int firstIndex, int secondIndex) throws SQLException {
        PreparedStatement statement = databaseConnection.prepareStatement("SELECT data FROM logs WHERE row_id >= ? AND row_id <= ? ORDER BY row_id");
        statement.setInt(1, firstIndex);
        statement.setInt(2, secondIndex);
        ResultSet set = statement.executeQuery();
        List<String> retList = new ArrayList<>();
        while (set.next()) {
            retList.add(set.getString("data"));
        }
        String[] retArray = new String[retList.size()];
        return retList.toArray(retArray);
    }

    private static class GetDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String pathDelimiter = "/";
            String[] pathArray = path.split(pathDelimiter);
            String[] defPathArray = GET_DATA_PATH_STRING.split(pathDelimiter);
            if (path.substring(0, GET_DATA_PATH_STRING.length()).equals(GET_DATA_PATH_STRING)) {
                StringBuilder builderServerString = new StringBuilder();
                for (int i = defPathArray.length; i < pathArray.length; i++) {
                    builderServerString.append(pathArray[i]);
                    builderServerString.append(pathDelimiter);
                }
                builderServerString.deleteCharAt(builderServerString.length() - 1);
                byte[] file = Files.readAllBytes(Paths.get(builderServerString.toString()));
                Headers header = exchange.getResponseHeaders();
                String dot = "\\.";
                String[] fileName = pathArray[pathArray.length - 1].split(dot);
                String fileType = fileName[fileName.length - 1];
                if (fileType.equals("js") || fileType.equals("css") || fileType.equals("jpg")) {
                    if (fileType.equals("js")) {
                        header.set("Content-Type", "application/javascript; charset=utf-8");
                    } else {
                        if (fileType.equals("css")) {
                            header.set("Content-Type", "text/css; charset=utf-8");
                        } else {
                            if (fileType.equals("jpg")) {
                                header.set("Content-Type", "image/jpeg");
                            }
                        }
                    }
                    exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, file.length);
                    OutputStream stream = exchange.getResponseBody();
                    stream.write(file);
                    stream.close();
                } else {
                    exchange.sendResponseHeaders(HTTP_ACCESS_DENIED, 0);
                    OutputStream stream = exchange.getResponseBody();
                    stream.close();
                }
            } else {
                exchange.sendResponseHeaders(HTTP_ACCESS_DENIED, 0);
                OutputStream stream = exchange.getResponseBody();
                stream.close();
            }
        }
    }

    private static class NextChunkHandler implements HttpHandler {
        private class ErrorMessage// JSON CONVERSION CLASS
        {
            ErrorMessage() {
                errorOnServer = -1;
            }

            private int errorOnServer;
        }

        private class ChunkMessage {
            private class Pair {
                Pair(int id, String t) {
                    row_id = id;
                    text = t;
                }

                int row_id;
                String text;
            }

            ChunkMessage(String[] array, int[] indexes) {
                if (array.length == indexes.length) {
                    pairArray = new ArrayList<>();
                    for (int i = 0; i < array.length; i++) {
                        NextChunkHandler.ChunkMessage.Pair newPair = new NextChunkHandler.ChunkMessage.Pair(indexes[i], array[i]);
                        pairArray.add(newPair);
                    }
                }
            }

            ArrayList<NextChunkHandler.ChunkMessage.Pair> pairArray;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String delimiter = "=";
            String[] parts = query.split(delimiter);
            int numberOfParts = 2;
            int firstIndex = -1;
            if (parts.length == numberOfParts && parts[0].equals("index")) {
                try {
                    firstIndex = Integer.parseInt(parts[1]);
                    int secondIndex = firstIndex + chunkSize - 1;
                    try {
                        int maxIndex = getMaxIndexInDatabase();
                        if (secondIndex > maxIndex) {
                            secondIndex = maxIndex;
                        }
                        String[] strings = getMainPageStringsInDatabase(firstIndex, secondIndex);
                        int[] indexes = IntStream.range(firstIndex, secondIndex + 1).toArray();
                        byte[] jsonData = parser.toJson(new NextChunkHandler.ChunkMessage(strings, indexes)).getBytes();
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, jsonData.length);
                        OutputStream stream = exchange.getResponseBody();
                        stream.write(jsonData);
                        stream.close();
                    } catch (SQLException ee) {
                        System.err.println("SERVER FATAL ERROR. Data can't send to site. Message: " + ee.getMessage());
                        sendErrorMessage(exchange);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("SERVER FATAL ERROR. Data can't send to site. Message: " + e.getMessage());
                    sendErrorMessage(exchange);
                }
            } else {
                System.err.println("SERVER FATAL ERROR. Query error. Query: " + query);
                sendErrorMessage(exchange);
            }
        }

        private void sendErrorMessage(HttpExchange exchange) {
            String errorData = parser.toJson(new NextChunkHandler.ErrorMessage());
            byte[] errorDataByte = errorData.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            OutputStream stream = null;
            try {
                exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, errorDataByte.length);
                stream = exchange.getResponseBody();
                stream.write(errorDataByte);
                stream.close();
            } catch (IOException e) {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ee) {

                }
            }
        }
    }

    private static class PrevChunkHandler implements HttpHandler {
        private class ErrorMessage// JSON CONVERSION CLASS
        {
            ErrorMessage() {
                errorOnServer = -1;
            }

            private int errorOnServer;
        }

        private class ChunkMessage {
            private class Pair {
                Pair(int id, String t) {
                    row_id = id;
                    text = t;
                }

                int row_id;
                String text;
            }

            ChunkMessage(String[] array, int[] indexes) {
                if (array.length == indexes.length) {
                    pairArray = new ArrayList<>();
                    for (int i = 0; i < array.length; i++) {
                        Pair newPair = new Pair(indexes[i], array[i]);
                        pairArray.add(newPair);
                    }
                }
            }

            ArrayList<Pair> pairArray;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String delimiter = "=";
            String[] parts = query.split(delimiter);
            int numberOfParts = 2;
            int maxSiteIndex = -1;
            if (parts.length == numberOfParts && parts[0].equals("index")) {
                try {
                    maxSiteIndex = Integer.parseInt(parts[1]);
                    int firstIndex = maxSiteIndex - chunkSize + 1;
                    if (firstIndex < 1) {
                        firstIndex = 1;
                    }
                    try {
                        String[] strings = getMainPageStringsInDatabase(firstIndex, maxSiteIndex);
                        int[] indexes = IntStream.range(firstIndex, maxSiteIndex + 1).toArray();
                        byte[] jsonData = parser.toJson(new ChunkMessage(strings, indexes)).getBytes();
//                        if(maxSiteIndex == 25)
//                        {
//                            System.err.println(jsonData.length);
//                        }
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, jsonData.length);
                        OutputStream stream = exchange.getResponseBody();
                        stream.write(jsonData);
                        stream.close();
                    } catch (SQLException ee) {
                        System.err.println("SERVER FATAL ERROR. Data can't send to site. Message: " + ee.getMessage());
                        sendErrorMessage(exchange);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("SERVER FATAL ERROR. Data can't send to site. Message: " + e.getMessage());
                    sendErrorMessage(exchange);
                }
            } else {
                System.err.println("SERVER FATAL ERROR. Query error. Query: " + query);
                sendErrorMessage(exchange);
            }
        }

        private void sendErrorMessage(HttpExchange exchange) {
            String errorData = parser.toJson(new ErrorMessage());
            byte[] errorDataByte = errorData.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            OutputStream stream = null;
            try {
                exchange.sendResponseHeaders(HTTP_RESPONSE_SUCCESS, errorDataByte.length);
                stream = exchange.getResponseBody();
                stream.write(errorDataByte);
                stream.close();
            } catch (IOException e) {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ee) {

                }
            }
        }
    }

    private void initDatabase() {
        String databaseUrl = "jdbc:postgresql://" + databaseHost + "/" + databaseName;
        Properties property = new Properties();
        property.put("user", databaseUser);
        property.put("password", databaseUserPassword);
        try {
            databaseConnection = DriverManager.getConnection(databaseUrl, property);
        } catch (SQLException e) {
            isDatabaseWork = false;
            System.err.println("ERROR. SQL DATABASE with name: " + databaseUrl + " . Message: \n" + e.getMessage());
        }
        isDatabaseWork = true;
        System.out.println("Connection with database: " + databaseUrl + " was established successfully");
    }

    private WebsiteServer(String ipAddress, String port) throws NumberFormatException, UnknownHostException, IOException {
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            System.err.println("Incorrect port number. Try values between 0 and 65536. Message: " + e.getMessage());
            throw e;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(ipAddress);
            server = HttpServer.create(new InetSocketAddress(address, portNumber), 0);
        } catch (UnknownHostException eee) {
            System.err.println("Incorrect ip address. Try another. Message: " + eee.getMessage());
            throw eee;
        } catch (IOException ee) {
            System.err.println("Server can't be created. Message: " + ee.getMessage());
            throw ee;
        }
        server.createContext(MAIN_PAGE_PATH_STRING, new MainPageHandler());
        server.createContext(GET_DATA_PATH_STRING, new GetDataHandler());
        server.createContext(GET_NEXT_CHUNK_PATH_STRING, new NextChunkHandler());
        server.createContext(GET_PREV_CHUNK_PATH_STRING, new PrevChunkHandler());
        server.createContext(GET_MAX_INDEX_PATH_STRING, new GetMaxIndexHandler());
        server.setExecutor(null);
        initDatabase();
        System.out.println(server.getAddress().toString());
    }

    public static WebsiteServer createWebsiteServer(String ipAddress, String port) throws NumberFormatException, UnknownHostException, IOException {
        return new WebsiteServer(ipAddress, port);
    }

    public void start() {
        if (isDatabaseWork) {
            server.start();
        }
    }
}
