

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Client {
    
    private InetAddress serverAddress;
    private int serverPort;

    private PrintWriter out_;

    private static final int MAX_RETRIES = 5;
    private static final int BASE_DELAY_MS = 1000; // 1 second initial delay

    private static final int PING_INTERVAL_MS = 10_000;
    private static final int PONG_TIMEOUT_MS = 20_000;

    private long lastPongTime;
    private String token_;

    private boolean shouldQuit = false;
    
    public Client(String addr, int port) throws IOException {
        this.serverAddress = InetAddress.getByName(addr);
        this.serverPort = port;
    }

    private SSLSocket createSocket() throws IOException {
        SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        Socket plainSocket = new Socket();
        plainSocket.connect(new InetSocketAddress(serverAddress, serverPort), 3_000);

        // upgrade to SSL over this connected socket
        SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(
            plainSocket,
            serverAddress.getHostAddress(),
            serverPort,
            true // autoClose the underlying socket on close
        );

        return sslSocket;
    }


    public void run() {
        // Takes user input and sends it to server
        Thread inputThread = new Thread(() -> {
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            try {
                while (!Thread.interrupted()){
                    String inputLine = userInput.readLine();
                    if (inputLine == null) break; // remove?
                    if (out_ != null) {
                        out_.println(inputLine);
                        // System.out.println("Sent line to server: " + inputLine);
                        if (inputLine.equalsIgnoreCase("quit")) {
                            shouldQuit = true;
                            System.out.println("Exiting client...");
                            break; // Exit the loop
                        }
                    }
                     else 
                        System.out.println("Output stream is null, cannot send input to server.");
                                 
                }
                System.out.println("Input thread interrupted, stopping...");
            } catch (IOException e) {
                System.err.println("Error reading user input: " + e.getMessage());
                Thread.currentThread().interrupt(); // remove?
            }
            
            System.out.println("Thread interrupted");
        });
        inputThread.start(); 
    
        int attempt = 0;
        while (!shouldQuit && attempt < MAX_RETRIES) {
            System.out.println("Attempting to connect to server...");
            attempt++;   
        
            try {
                SSLSocket socket = createSocket();
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                this.out_ = out;
                // Reset pong time
                lastPongTime = System.currentTimeMillis();

                System.out.println("Connected to server! :)");
                attempt = 0; // Reset attempt counter on successful connection
                
                // Start a thread to listen for server messages
                Thread listenerThread = Thread.ofVirtual().start(() -> {
                    String line;
                    try {
                        while ((line = in.readLine()) != null) {
                            // Do not print the token to the console
                            if (line.startsWith("TOKEN")) {
                                String token = line.split(" ")[1]; 
                                token_ = token;
                                continue; // Skip printing the token
                            } 

                            if (line.startsWith("EXPIRED")){
                                token_ = null;
                            }

                            if (line.startsWith("PONG")) {
                                lastPongTime = System.currentTimeMillis();
                                // System.out.println("Received pong from server"); 
                                continue; // Skip printing the PONG message
                            }
                            System.out.print( line + "\n");
                        }
                        
                    } catch (IOException e) {
                        System.err.println("Error reading from server: " + e.getMessage());
                    }
                });    
                
                // Connection may not be the first, so, if the token is not null, we need to send it
                if (token_ != null) {
                    System.out.println("Trying to restore session...");
                    out_.println("reconnect " + token_);
                }
                
                Thread pingThread = new Thread(() -> {
                    try {
                        System.out.println("Starting ping thread...");
                        while (!Thread.interrupted()) {

                            Thread.sleep(PING_INTERVAL_MS);
                            // System.out.println("Sent ping to server, waiting for pong...");

                            if (System.currentTimeMillis() - lastPongTime > PONG_TIMEOUT_MS) {
                                System.out.println("Pong timeout, server may be down");
                                listenerThread.interrupt(); 
                                break;
                            }

                            out_.println("ping");
                        }
                    } catch (InterruptedException e) {
                        // Thread interrupted, exit gracefully
                    }
                });
                pingThread.start();
                 
                listenerThread.join(); // Main thread waits here until the server disconnects

                socket.close(); // Close the socket
                out.close(); // Close the output stream
                in.close(); // Close the input stream
                pingThread.interrupt(); // Interrupt the ping thread

                // If we reach here, connection ended -> restart loop
                System.out.println("Connection closed. ");
        
            } catch (Exception e) {
                handleConnectionFailure(attempt, e);
            }
        }
        if (!shouldQuit) 
            System.err.println("Failed to connect after " + MAX_RETRIES + " attempts. Exiting...");
        else
         System.out.println("User requested quit, stopping client...");

        inputThread.interrupt(); // Interrupt the input thread
    }
    

    private int calculateBackoffTimeout(int attempt) {
        return (int) (BASE_DELAY_MS * Math.pow(2, attempt));
    }

    private void handleConnectionFailure(int attempt, Exception e) {
        int delay = calculateBackoffTimeout(attempt);
        System.err.printf("Connection attempt %d failed. Retrying in %dms... (%s)%n",
            attempt, delay, e.getMessage());
        
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "server.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    
        if (args.length != 2) {
            System.err.println("Usage: java ChatClient <address> <port>");
            return;
        }

        String address = args[0];
        int port = Integer.parseInt(args[1]);
    
        try {
            Client client = new Client(address, port);
            client.run();
        } catch (IOException e) {
            System.err.println("Error initializing ChatClient: " + e.getMessage());
        }
    }
}
