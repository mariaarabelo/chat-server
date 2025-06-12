class Session {
    private final String username;
    private final long expirationTime;
    private Room room;
    
    Session(String username, long expirationTime) {
        this.username = username;
        this.expirationTime = expirationTime;
    }

    public boolean isValid() {
        return System.currentTimeMillis() < expirationTime;
    }

    public String getUsername() {
        return username;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }
}
