Socket Java API for network communication

Three problems:
1. Datagram Socket Programming
2. TCP Socket Programming
3. Threads and Concurrency

## Application

- Server collects sensor readings sent by sensors (clients) -> **put**, used by sensors to send their reading, one at a time. No response message.
- Server provides statistics on these readings upon demand by other clients -> **get**, used by other clients to retrieve the average of all values put by a given sensor

- values given and averages are floating point numbers.
- sensors are identified by integer ids.

- the messages should be strings.

# Datagram Socket Programming - UDP version

- The communicating processes should use the DatagramSocket class.

The server shall be invoked as: 
```java Server <port> <no_sensors>```
- ```<port>``` is the port number that the Server shall use to receive messages from producers;
- ```<no_sensors>``` is the number of sensors managed by the Server.

- To support several sensors, the server can use an array of objects of a class Sensor, using the sensor id to access the respective array element.

The client shall be invoked as:
```java Client <addr> <port> <op> <id> [<val>]```
- ```<addr>``` is the IPv4 addr of the server's computer;
- ```<port>``` is the port number that the server shall use to receive messages from clients;
- ```<op>``` is the operation to perform (put or get)
- ```<id>``` is the sensor id
- ```<val>``` is the float value that the client shall send, if a put operation

If a get operation, the client should 
- display the sensor id and its average value 
- terminate

If a put operation, the client should
- send each of the values in its own message with an interval of 1 second between messages

-> use Thread.sleep() to measure time intervals

-> use setSoTimeout() to prevent the client of hanging forever, in case it is started before the server and sends a get request.


## Compiling and Running
``` 
 cd src/udp
 javac -d bin src/udp/*.java

 //javac *.java
 java Server 9876 5
 java Client localhost 9876 put 0 23.5
 java Client localhost 9876 get 0
```
or 

``` 
 # Navigate to your project root

# Compile all Java files (including package structure)
javac -d bin src/tcp/*.java 

# Run the TCPServer with proper package specification
java -cp bin tcp.TCPServer 9876 5
 java -cp bin tcp.TCPClient localhost 9876 put 0 23.5
 java -cp bin tcp.TCPClient localhost 9876 get 0
```


# TCP Socket Programming

Uses TCP as the transport protocol, thus communicating processes should use:
- Socket class
- ServerSorcket class

If the client starts and the server is not running, the client would terminate immediately.
To avoid this, we should
- try to connecy multiple times
- change the interval between tries (using an approach similar to the exponential back-off used in Ethernet)

## Exponential Backoff
It is a network retry strategy, where the delay between retry attemps incerases exponentially.

# Threads and Concurrency - Multithreaded Version



# Project - Chat

- Users must authenticate.
- We may provide a sub protocol for user registration.
- We may persist the registration data in a file. Alternatively, we may provide a file with registration data.

- We may use secure channels (i.e. package javax.net.ssl):
    - Using SSL/TLS encryption for all network communication between clients and servers, preventing eavesdropping.
    - Generate SSL certificates, use SSLSocket/SSLServerSocket instead of regular sockets -> ensures all messages are encrypted.

- When a user chats in a room, their name should precede the message. Example:
 Room: Library
 [Alice enters the room]
 Alice: Hi people! Anyone?
 [Eve enters the room]
 Eve: I am giving free apples and Eve memecoin.
 Alice: Give me an apple please

 - Example of AI chat room: "AI doodle", which summarize all user availability suggestions and propose a common meeting time. "Bot" is an extra user.

 - Important: no race conditions in the access to shared data structures or in synchronization between threads! 

- Minimize thread overheads (i.e., use the recently introduced Java virtual threads - use Java SE 21 or more recent.)

- Avoid slow clients from bringing the system to a crawl:
    - Use non-blocking I/O or virtual threads
    - Process messages asynchronously
    - Use timeouts for client operations

Important! The implementation should tolerate broken TCP connections (the user state should not be lost)
- If the connection is broken while the user is in a room, the client should reconnect and the user will resume its session in the same chat room, without having to authenticate again or rejoining the room, the server will start relaying the messages received on the chat room using the new connection.
- The client should not cache user credentials (this is not secure)
- The client should use a token (similar to an HTTP cookie) sent by the server, that the client shall send upon reconnecting, allowing the server binding the new TCP connection with the user.
- An expiration time must be associate to every token. 