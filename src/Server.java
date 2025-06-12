

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class Server {
    private final int port;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Lock roomLock = new ReentrantLock();
    private AuthManager authManager;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        authManager = new AuthManager("data/users.txt");
        loadRoomsFromFile("data/rooms.txt");

        SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
        serverSocket.setEnabledCipherSuites(new String[] { 
            "TLS_AES_128_GCM_SHA256", 
            "TLS_AES_256_GCM_SHA384" 
        });

        System.out.println("Server started on port " + port);
        while (true) {
            try {
                SSLSocket clientSocket = (SSLSocket)serverSocket.accept();
                Thread.startVirtualThread(new ClientHandler(clientSocket, rooms, roomLock, authManager));
            } catch (SSLException e) {
                System.err.println("SSL Handshake failed: " + e.getMessage());
                continue; // Keep server running
            } catch (IOException e) {
                System.err.println("IOException: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void loadRoomsFromFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String roomName;
            while ((roomName = reader.readLine()) != null) {
                roomName = roomName.trim();
                if (!roomName.isEmpty()) {
                    rooms.put(roomName, new Room(roomName));
                }
            }
        }
    }

    public static void main(String[] args) {

        System.setProperty("javax.net.ssl.keyStore", "server.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

        int port = 5000;
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }

        Server server = new Server(port);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
