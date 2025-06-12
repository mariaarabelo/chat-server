

import java.io.*;

import java.net.Socket;
import java.net.SocketException;

import java.util.Map;
import java.util.concurrent.locks.Lock;

public class ClientHandler implements Runnable {

    private static final String HELP_MESSAGE = String.join("\n",
        "## Available commands: ##",
        "auth <username> <password> - Authenticate or register",
        "join <room> - Join or create a room",
        "msg <message> - Send a message to the current room",
        "leave - Leave current room",
        "list - List all rooms",
        "who - List room participants",
        "help - Show this message",
        "quit - Exit chat",
        "If you want to connect to a room with an AI, checkout the room AI lounge ;)",
        "####################################"

    );
    
    private final Socket clientSocket;
    private final Map<String, Room> chatRooms;
    private final Lock roomsLock;
    private final AuthManager authManager;

    private static final long TIMEOUT_MS = 20000; 
    private long lastPingTime = System.currentTimeMillis();

    
    private BufferedReader input;
    private PrintWriter output;

    private User currentUser;
    private String currentToken;
    private Room currentRoom;

    private boolean running = true;

    public ClientHandler(Socket socket, Map<String, Room> rooms, Lock lock, AuthManager authManager) 
        throws SocketException{
        this.clientSocket = socket;
        this.chatRooms = rooms;
        this.roomsLock = lock;
        this.authManager = authManager;
        
        System.out.println("New Client Handler");
    }

    @Override
    public void run() {
        try {
            initializeStreams();
            startTimeoutChecker();
            handleCommands();
            System.out.println("Client disconnected");
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            System.out.println("Finally block being executed");
            cleanup();
        }
       
    }

    private void initializeStreams() throws IOException {
        input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        output = new PrintWriter(clientSocket.getOutputStream(), true);
    }
    
    private boolean handleAuthCommand(String command) {        
        String[] parts = command.split(" ");
        if (parts.length != 2) {
            output.println("Invalid format, please use: auth <username> <password>");
            return false;
        }

        String username = parts[0];
        String password = parts[1];
        
        String result = authManager.authenticateOrRegister(username, password);
        String token;
        switch (result) {
            case "OK":
                token = authManager.generateSessionToken(username);
                currentToken = token;
                currentUser = new User(username, output);
                output.println("Welcome back, " + username);
                output.println("TOKEN " + token);
                return true;
            case "NEW_USER":
                token = authManager.generateSessionToken(username);
                currentToken = token;
                currentUser = new User(username, output);
                output.println("Account created. Welcome, " + username);
                output.println("TOKEN " + token);
                return true;
            case "WRONG_PASSWORD":
                output.println("AUTH_FAILURE Incorrect password");
                return false;
        }
        return false; // Default case
    }

    private void startTimeoutChecker() {
        new Thread(() -> {
            try {
                while (!clientSocket.isClosed()) {
                    if (System.currentTimeMillis() - lastPingTime > TIMEOUT_MS) {
                        System.out.println("Client timed out ");
                        clientSocket.close(); // will trigger IOException in main loop
                        break;
                    }
                    Thread.sleep(10_000);
                }
            } catch (InterruptedException | IOException ignored) {}
        }).start();
    }

    private void handleCommands()  {
        output.println("Welcome to the chat server! Type 'help' for a list of commands.");
        
        String command;
        try {
            while (running && (command = input.readLine()) != null) {
                lastPingTime = System.currentTimeMillis(); 
                handleCommand(command);
            }
        } catch (IOException e) {
            output.println("Error reading input: " + e.getMessage());
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();;
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "auth":
                handleAuthCommand(args);
                break;
            case "reconnect":
                handleReconnectCommand(args);
                break;
            case "ping":
                System.out.println("Received ping from client");
                output.println("PONG");
                break;
            case "join":
                if (!isAuthenticated()) {
                    output.println("You are not authenticated. Please authenticate first.");
                    return;
                }
                handleJoinCommand(args);
                break;
            case "msg":
                if (!isAuthenticated()) {
                    output.println("You are not authenticated. Please authenticate first.");
                    return;
                }
                handleMsgCommand(command);
                break;
            case "leave":
                handleLeaveCommand();
                break;
            case "list":
                handleListCommand();
                break;
            case "who":
                if (!isAuthenticated()) {
                    output.println("You are not authenticated. Please authenticate first.");
                    return;
                }
                handleWhoCommand();
                break;
            case "help":
                handleHelpCommand();
                break;
            case "quit":
                output.println("Goodbye!");
                running = false;
                break;
            default:
                output.println("Unknown command " + cmd);
        }
    }

    private void handleReconnectCommand(String token){

        if (authManager.validateToken(token)) {
            Session lastSession = authManager.getSessionFromToken(token);
            currentToken = token;

            String username = lastSession.getUsername();
            currentUser = new User(username, output);

            Room lastRoom = lastSession.getRoom();
            output.println("Reconnection successful as " + username);

            if (lastRoom != null) {
                roomsLock.lock();
                try {
                    lastRoom.addParticipant(currentUser);
                    currentRoom = lastRoom;
                    output.println("Reconnected to room " + lastRoom.getName());
                } finally {
                    roomsLock.unlock();
                }
            } else {
                output.println("Reconnected successfully, but you were not in any room.");
            }

            return;
        }
        output.println("Invalid or expired token"); 
        
    }
    
    private boolean isAuthenticated() {
        if (currentUser == null) {
            output.println("Current user is null. Please authenticate first.");
            return false;
        }
        if (currentToken == null) {
            output.println("Current token is null, no active session. Please authenticate first.");
            return false;
        }
        if (!authManager.validateToken(currentToken)) {
            output.println("Invalid or expired token. Please authenticate again.");
            return false;
        }
        return true;
    }

    private void logoutUser(){
        if (currentToken == null) {
            output.println("No active session to logout from.");
            return;
        }

        if (currentRoom == null) {
            output.println("No active room to logout from.");
            return;
        }
        if (currentRoom != null) {
            currentUser.close(); 
            currentRoom.removeParticipant(currentUser);
            output.println("You were removed from the room");
        }        
        currentUser = null;
        currentRoom = null;
    }

    private void cleanup() {
        System.out.println("Cleaning up resources...");
        logoutUser();

        try {
            output.println("Closing socket...");
            clientSocket.close();
            input.close();
            output.close();
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    private void handleJoinCommand(String roomName) {
        roomsLock.lock();
        try {
            // Handle AI room creation
            if (roomName.startsWith("AI")) {
                String[] aiParts = roomName.split(":", 2);
                if (aiParts.length != 2) {
                    output.println("\n**************************\nWelcome to the AI lounge!\n**************************");
                    output.println("Here you can create your own AI room\n");
                    output.println("The AI is listening to coversation, but if you want it to chime in, call her with @AI!\n");
                    output.println("Usage: join AI:<roomname>");
                    return;
                }
                
                String actualRoomName = aiParts[1].trim();
                currentRoom = chatRooms.computeIfAbsent(actualRoomName, 
                    k -> {
                        try {
                            return new AIRoom(actualRoomName);
                        } catch (IOException e) {
                            output.println("Error creating AI room: " + e.getMessage());
                            return null;
                        }
                    });
            } 
            // Regular room
            else {
                currentRoom = chatRooms.computeIfAbsent(roomName, Room::new);
            }
    
            if (currentRoom != null) {
                output.println("Joined room: " + currentRoom.getName());
                currentRoom.addParticipant(currentUser);
            }

            // Update the session token
            authManager.getSessionFromToken(currentToken).setRoom(currentRoom);
            System.out.println("currentRoom: " + authManager.getSessionFromToken(currentToken).getRoom().getName());
        } finally {
            roomsLock.unlock();
        }
    }

    private void handleMsgCommand(String command) {
        if (currentRoom == null) {
            output.println("Not in any room");
            return;
        } 
        String message = command.substring(4); // Skip "msg "
        currentRoom.addMessage(new Message(currentUser.getUsername(), message));
    }
    
    private void handleLeaveCommand() {
        if (currentUser == null) {
            output.println("You are not authenticated. Please authenticate first and enter a room.");
            return;
        }

        if (currentRoom == null) {
            output.println("Currently, you are not in any room. Type 'leave' when you are in a room to leave it.");
            return;
        }
        
        currentRoom.removeParticipant(currentUser);
        output.println("You just left room " + currentRoom.getName());

        // Update the session token
        authManager.getSessionFromToken(currentToken).setRoom(null);
        
    }
    
    private void handleListCommand() {
        roomsLock.lock();
        try {
            if (chatRooms.isEmpty()) {
                output.println("No rooms available");
                return;
            }
            
            output.println("Available rooms:");
            chatRooms.keySet().forEach(output::println);
        } finally {
            roomsLock.unlock();
        }
    }
    
    private void handleWhoCommand() {
        if (currentRoom == null) {
            output.println("Not in any room");
            return;
        }
        
        output.println("Room participants:");
        currentRoom.getParticipants().stream()
            .map(User::getUsername)
            .forEach(output::println);
    }
    

    private void handleHelpCommand() {
        output.println(HELP_MESSAGE);
    }
}
