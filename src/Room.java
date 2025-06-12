
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Represents a chat room where users can join, leave, and send messages.
 */

public class Room {

    private final String name;
    private final Set<User> participants;
    private final Lock roomLock;
    
    protected final LinkedList<Message> history;
    
    public Room(String name) {
        this.name = name;
        this.history = new LinkedList<>();
        this.participants = new HashSet<>();
        this.roomLock = new ReentrantLock();
    }

    public String getName() {
        return name;
    }

    public Set<User> getParticipants() {
        roomLock.lock();
        try {
            return Collections.unmodifiableSet(participants);
        } finally {
            roomLock.unlock();
        }
    }

    public void addParticipant(User user) {
        roomLock.lock();
        try {
            participants.add(user);
            broadcast(systemMessage("Hey, " + user.getUsername() + " just joined the chat room " + getName() + "!"));
        } finally {
            roomLock.unlock();
        }
    }

    public void removeParticipant(User user) {
        System.out.println("Removing participant " + user.getUsername() + " from room " + name);	
        roomLock.lock();
        try {
            participants.remove(user);
            broadcast(systemMessage(user.getUsername() + " left the room"));
        } finally {
            roomLock.unlock();
        }
    }

    public void addMessage(Message message) {
        roomLock.lock(); 
        try {
            history.add(message);
            broadcast(message);
        } finally {
            roomLock.unlock();
        }
    }

    public void broadcast(Message message) {
        String formattedMessage = String.format("[%s]: %s", 
            message.getSender(), 
            message.getContent());

        for (User participant : participants) 
            participant.send(formattedMessage);    
    }

    
    protected Message systemMessage(String content) {
        return new Message("System", content);
    }
}
