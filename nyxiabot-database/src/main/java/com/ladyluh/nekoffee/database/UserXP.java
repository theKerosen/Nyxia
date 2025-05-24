package com.ladyluh.nekoffee.database;

public class UserXP {
    private final String guildId;
    private final String userId;
    public int xp;
    private int level;
    private long lastMessageTimestamp;

    public UserXP(String guildId, String userId, int xp, int level, long lastMessageTimestamp) {
        this.guildId = guildId;
        this.userId = userId;
        this.xp = xp;
        this.level = level;
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public UserXP(String guildId, String userId) {
        this(guildId, userId, 0, 0, 0);
    }


    public String getGuildId() {
        return guildId;
    }

    public String getUserId() {
        return userId;
    }

    public int getXp() {
        return xp;
    }

    public int getLevel() {
        return level;
    }

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }


    public void setXp(int xp) {
        this.xp = xp;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }
}