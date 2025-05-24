package com.ladyluh.nekoffee.database;


public class TemporaryChannelRecord {
    public String channelId;
    public String guildId;
    public String ownerUserId;
    public long createdAtTimestamp;
    public Integer locked;

    public TemporaryChannelRecord(String channelId, String guildId, String ownerUserId, long createdAtTimestamp, Integer locked) {
        this.channelId = channelId;
        this.guildId = guildId;
        this.ownerUserId = ownerUserId;
        this.createdAtTimestamp = createdAtTimestamp;
        this.locked = locked;
    }
}
