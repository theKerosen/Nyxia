package com.ladyluh.nekoffee.database;

// Representa uma linha na tabela guild_configs
public class GuildConfig {
    public String guildId;
    public String logChannelId;
    public String welcomeChannelId;
    public String autoAssignRoleId;
    public String tempHubChannelId;
    public String tempChannelCategoryId;
    public String tempChannelNamePrefix; // "Sala de "
    public Integer defaultTempChannelUserLimit; // Null ou 0 para ilimitado
    public Integer defaultTempChannelLock; // False para destrancado

    public GuildConfig(String guildId, String logChannelId, String welcomeChannelId, String autoAssignRoleId, String tempHubChannelId, String tempChannelCategoryId, String tempChannelNamePrefix, Integer defaultTempChannelUserLimit, Integer defaultTempChannelLock) {
        this.guildId = guildId;
        this.logChannelId = logChannelId;
        this.welcomeChannelId = welcomeChannelId;
        this.autoAssignRoleId = autoAssignRoleId;
        this.tempHubChannelId = tempHubChannelId;
        this.tempChannelCategoryId = tempChannelCategoryId;
        this.tempChannelNamePrefix = tempChannelNamePrefix;
        this.defaultTempChannelUserLimit = defaultTempChannelUserLimit;
        this.defaultTempChannelLock = defaultTempChannelLock;
    }

    // Construtor para uma nova config (com padrões)
    public GuildConfig(String guildId) {
        this.guildId = guildId;
        this.logChannelId = "";
        this.welcomeChannelId = "";
        this.autoAssignRoleId = "";
        this.tempHubChannelId = "";
        this.tempChannelCategoryId = "";
        this.tempChannelNamePrefix = "Sala de "; // Usar padrão do ConfigManager ou listener
        this.defaultTempChannelUserLimit = 5; // Usar padrão do ConfigManager ou listener
        this.defaultTempChannelLock = 0; // Usar padrão do ConfigManager ou listener
    }
}