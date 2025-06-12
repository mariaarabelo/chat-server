

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;

public class User {
    private final String username;
    private final PrintWriter out;
    private PrintWriter fileWriter;

    public User(String username, PrintWriter out) {
        this.username = username;
        this.out = out;

        try {
            String filePath = "./temp/output_" + username + ".txt";
            this.fileWriter = new PrintWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            System.err.println("Error creating file writer: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public void send(String message) {
        fileWriter.println(message);
        fileWriter.flush();
    }

    public void close() {
        System.out.println("Limpando e fechando o ficheiro de mensagens do usuário: " + username);
        try (PrintWriter cleaner = new PrintWriter(new FileWriter("./temp/output_" + username + ".txt", false))) {

        } catch (IOException e) {
            System.err.println("Error cleaning the file: " + e.getMessage());
        }

        fileWriter.close();
    }
}
