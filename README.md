# 💬 Chat Server & Client 
A multi-room, token-authenticated chat system built in Java using virtual threads.


## 🧩 Requirements

- Java **21 or later**. Make sure you have 21 version by running:
```
sudo apt update
sudo apt install openjdk-21-jdk
```
- Preview features enabled (for virtual threads)
- The model we use is **llama3**, but it can be changed in file AIRoom.java in "private static final String OLLAMA_MODEL = "llama3";"

---

## 🧪 How to Run in Windows

In a terminal, compile the project and start the server:
```
rm -r bin/* 

javac -cp ".;lib/json-20231013.jar;lib/jbcrypt-0.4.jar" src/*.java -d bin 

java --enable-preview -cp ".;bin;lib/json-20231013.jar;lib/jbcrypt-0.4.jar" Server 5000
```

In another terminal, start a client:
```
java --enable-preview -cp ".;bin;lib/json-20231013.jar;lib/jbcrypt-0.4.jar" Client 127.0.0.1 5000 
```

## 🧪 How to Run in Linux

In a terminal, compile the project and start the server:
```
rm -r bin/* 

javac -cp ".:lib/json-20231013.jar:lib/jbcrypt-0.4.jar" src/*.java -d bin 

java --enable-preview -cp ".:bin:lib/json-20231013.jar:lib/jbcrypt-0.4.jar" Server 5000
```

In another terminal, start a client:
```
java --enable-preview -cp ".:bin:lib/json-20231013.jar:lib/jbcrypt-0.4.jar" Client 127.0.0.1 5000 
```

To simulate a connection fall:
```
sudo iptables-legacy -A OUTPUT -p tcp --dport 5000 -j DROP
```

To restore the connection:
```
sudo iptables-legacy -D OUTPUT -p tcp --dport 5000 -j DROP
```


## 💬 Commands
```
// Authenticate
auth <username> <password> 

// Show all available commands
help 

// List all existing rooms
list 

// Join (or create) a chat room
join <roomname> 

// Leave the current room
leave 

// Show users in the current room
who 

// Send a message to the current room
msg <message> 

// Exit the chat system
quit 
```

## 💬 Commands for AI room
```
// Join AI lounge to create AI rooms
join AI lounge

// Join (or create) an AI room
join AI:<roomname> 

// Call AI 
msg @AI <message> 
```
## 🏠 Preloading 
Existent credentials:
- User: mari ; password: 1234 
- User: joao ; password: 1234 

List of default rooms, automatically created when the server starts:
- main
- library
- gaming