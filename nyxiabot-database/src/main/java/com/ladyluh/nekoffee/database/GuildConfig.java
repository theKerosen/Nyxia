package com.ladyluh.nekoffee.database;

public class GuildConfig {
    public String guildId;
    public String logChannelId;
    public String welcomeChannelId;
    public String autoAssignRoleId;
    public String recordingsChannelId;
    public String tempHubChannelId;
    public String tempChannelCategoryId;
    public String tempChannelNamePrefix;
    public Integer defaultTempChannelUserLimit;
    public Integer defaultTempChannelLock;
    public String joinSoundId; 

    public GuildConfig(String guildId, String logChannelId, String welcomeChannelId, String autoAssignRoleId, String recordingsChannelId, String tempHubChannelId, String tempChannelCategoryId, String tempChannelNamePrefix, Integer defaultTempChannelUserLimit, Integer defaultTempChannelLock, String joinSoundId) {
        this.guildId = guildId;
        this.logChannelId = logChannelId;
        this.welcomeChannelId = welcomeChannelId;
        this.autoAssignRoleId = autoAssignRoleId;
        this.recordingsChannelId = recordingsChannelId;
        this.tempHubChannelId = tempHubChannelId;
        this.tempChannelCategoryId = tempChannelCategoryId;
        this.tempChannelNamePrefix = tempChannelNamePrefix;
        this.defaultTempChannelUserLimit = defaultTempChannelUserLimit;
        this.defaultTempChannelLock = defaultTempChannelLock;
        this.joinSoundId = joinSoundId; 
    }

    public GuildConfig(String guildId) {
        this.guildId = guildId;
        this.logChannelId = "";
        this.welcomeChannelId = "";
        this.autoAssignRoleId = "";
        this.recordingsChannelId = "";
        this.tempHubChannelId = "";
        this.tempChannelCategoryId = "";
        this.tempChannelNamePrefix = "Sala de ";
        this.defaultTempChannelUserLimit = 5;
        this.defaultTempChannelLock = 0;
    }
}