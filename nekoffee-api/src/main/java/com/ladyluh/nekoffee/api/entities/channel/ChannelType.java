package com.ladyluh.nekoffee.api.entities.channel;

public enum ChannelType {
    GUILD_TEXT(0),
    DM(1),
    GUILD_VOICE(2),
    GROUP_DM(3),
    GUILD_CATEGORY(4),
    GUILD_ANNOUNCEMENT(5),
    ANNOUNCEMENT_THREAD(10),
    PUBLIC_THREAD(11),
    PRIVATE_THREAD(12),
    GUILD_STAGE_VOICE(13),
    GUILD_DIRECTORY(14),
    GUILD_FORUM(15),

    UNKNOWN(-1);

    private final int id;

    ChannelType(int id) {
        this.id = id;
    }

    public static ChannelType fromId(int id) {
        for (ChannelType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public int getId() {
        return id;
    }
}