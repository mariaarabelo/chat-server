

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Locale;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;

public class AIRoom extends Room {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String OLLAMA_MODEL = "llama3";
    private final HttpClient httpClient;
    private final Queue<Message> messageQueue = new LinkedList<>();

    private final Lock messageLock = new ReentrantLock();
    private final Condition messageAvailable = messageLock.newCondition();
    
    private volatile boolean isProcessing = false; // For animation control
    
    public AIRoom(String name) throws IOException {
        super(name);   
        this.httpClient = HttpClient.newHttpClient();
        startMessageProcessor();
        System.out.println("AI Room created: " + name);
    }

    @Override
    public void addMessage(Message message) {
        super.addMessage(message); // Always add to history and broadcast

        // Only queue message if it requires AI processing
        if (message.getContent().toLowerCase(Locale.ROOT).startsWith("@ai")) {
            messageLock.lock();
            try {
                messageQueue.add(message);
                System.out.println("Message queue is now: " + messageQueue);
                messageAvailable.signalAll(); // Wake up the processor thread
            } finally {
                messageLock.unlock();
            }
        }
    }

    private void startMessageProcessor() {
        new Thread(() -> {
            while (true) {
                try {
                    processNextMessage();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    // Ensures thread-safe access to queue and isProcessing variable
    private void processNextMessage() throws InterruptedException {
        Message message;

        messageLock.lock();
        try {
            while (messageQueue.isEmpty()) {
                messageAvailable.await(); 
            }
            message = messageQueue.poll();
        } finally {
            messageLock.unlock(); 
        }

        try {
            startAnimation(); 
            Message response = getAIResponse(message);
            System.out.println("AI response: " + response.getContent());
            super.addMessage(response);
        } finally {
            stopAnimation();
        }
    }

    private void startAnimation() {
        isProcessing = true;
        Thread.startVirtualThread(() -> {
            while (isProcessing) {
                broadcast(systemMessage("AI is thinking..."));
                try { Thread.sleep(5000); } 
                catch (InterruptedException e) { break; }
            }
        });  
    }

    private void stopAnimation() {
        isProcessing = false;
    }

    private Message getAIResponse(Message message)  {
        String prompt = buildPrompt(message);

        System.out.println("Prompt sent to the model:\n\n" + prompt.toString());

        String jsonRequest = new JSONObject()
            .put("model", OLLAMA_MODEL)
            .put("prompt", prompt)
            .put("stream", false)
            .toString();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OLLAMA_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .timeout(java.time.Duration.ofSeconds(30))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IOException("API Error: " + response.body());
            }

            return aiMessage(new JSONObject(response.body()).getString("response"));
        } catch (IOException | InterruptedException e) {
            return systemMessage("Error getting AI response: " +  e.getClass().getSimpleName() +  e.getMessage());
        }
    }  

    private Message aiMessage(String content) {
        return new Message("AI", content);
    }

    private String buildPrompt(Message currentMessage) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(String.format("%s just said ''%s''\n", currentMessage.getSender(), currentMessage.getContent()));
        prompt.append("The chat history is as follows:\n");
        
        for (int i = 0; i < this.history.size() - 1; i++) {
            Message msg = this.history.get(i);
            prompt.append(String.format("%s said ''%s''\n", msg.getSender(), msg.getContent()));
        }
        
        return prompt.toString();
    }

    
}
