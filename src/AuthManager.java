

import java.io.*;
import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.mindrot.jbcrypt.BCrypt;


/**
 * AuthManager is responsible for user authentication and registration.
 * It stores user credentials in a file and provides methods to authenticate or register users.
 */

public class AuthManager {
    private static final long TOKEN_EXPIRATION_MINUTES = 5;
    
    private final Map<String, String> credentials;
    private final String credentialsFilePath;
    private final Map<String, Session> activeSessions; // (token, session)
    private final Lock authLock;

    public AuthManager(String credentialsFilePath) throws IOException {
        this.credentialsFilePath = credentialsFilePath;
        this.credentials = new HashMap<>();
        this.activeSessions = new HashMap<>();
        this.authLock = new ReentrantLock();
        loadCredentials();
    }

    private void loadCredentials() {
        File file = new File(credentialsFilePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("Failed to create credentials file: " + e.getMessage());
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":", 2);
                if (parts.length == 2) {
                    credentials.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load credentials: " + e.getMessage());
        }
    }

    public String authenticateOrRegister(String username, String password) {
        authLock.lock();
        try {
            if (credentials.containsKey(username)) {
                 String storedHash = credentials.get(username);

                if (BCrypt.checkpw(password, storedHash)) 
                    return "OK";
                
                return "WRONG_PASSWORD";
                
            } else {
                String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
                credentials.put(username, hashed);
                registerCredentials(username, hashed);

                return "NEW_USER"; 
            }    
        } finally {
            authLock.unlock();
        }
    }

    private void registerCredentials(String username, String hashedPassword) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(credentialsFilePath, true))) {
            writer.write(username + ":" + hashedPassword);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String generateSessionToken(String username){
        authLock.lock();
        try {
            String token = UUID.randomUUID().toString();
            long expirationTime = System.currentTimeMillis() + 
                TimeUnit.MINUTES.toMillis(TOKEN_EXPIRATION_MINUTES);

            activeSessions.put(token, new Session(username, expirationTime)); 
            return token;
        } finally {
            authLock.unlock();
        }
    }  

    public boolean validateToken(String token) {
        authLock.lock();
        try {
            Session session = activeSessions.get(token);

            if (session == null) return false;
            if (!session.isValid()) {
                activeSessions.remove(token);
                System.out.println("Token expired.");
                return false;
            }
           return true;
        } finally {
            authLock.unlock();
        }
    }

    public Session getSessionFromToken(String token) {
        authLock.lock();
        try {
            Session session = activeSessions.get(token);
            return session;
        } finally {
            authLock.unlock();
        }
    }



}
