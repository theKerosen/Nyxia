package com.ladyluh.nekoffee.database;

public class UserChannelPreference {
    public String guildId;
    public String userId;
    public Integer preferredUserLimit;
    public String preferredName;
    public Integer defaultLocked;
    public Integer autoOwnerSwitching;

    public UserChannelPreference(String guildId, String userId, Integer preferredUserLimit, String preferredName, Integer defaultLocked, Integer autoOwnerSwitching) {
        this.guildId = guildId;
        this.userId = userId;
        this.preferredUserLimit = preferredUserLimit;
        this.preferredName = preferredName;
        this.defaultLocked = defaultLocked;
        this.autoOwnerSwitching = autoOwnerSwitching;
    }

    public UserChannelPreference(String guildId, String userId) {
        this(guildId, userId, 5, null, 0, 0);
    }
}