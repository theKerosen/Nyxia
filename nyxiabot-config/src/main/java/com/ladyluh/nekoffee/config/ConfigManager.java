package com.ladyluh.nekoffee.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class ConfigManager {
    private final Properties properties = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private final TreeMap<Integer, String> xpRoleMappings = new TreeMap<>(); // Mapas ordenados pela chave

    public ConfigManager() throws Exception {


        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Aviso: config.properties não encontrado. Usando variáveis de ambiente.");

                return;
            }
            properties.load(input);
            loadXPRoleMappings();

        }
    }

    public String getBotToken() {
        return properties.getProperty("BOT_TOKEN", System.getenv("NEKOFFEE_BOT_TOKEN"));
    }

    public String getLogChannelId() {
        return properties.getProperty("LOG_CHANNEL_ID", System.getenv("NEKOFFEE_LOG_CHANNEL_ID"));
    }

    public String getWelcomeChannelId() {
        return properties.getProperty("WELCOME_CHANNEL_ID", System.getenv("NEKOFFEE_WELCOME_CHANNEL_ID"));
    }

    public String getAutoAssignRoleId() {
        return properties.getProperty("AUTO_ASSIGN_ROLE_ID", System.getenv("NEKOFFEE_AUTO_ROLE_ID"));
    }

    public String getHubChannelId() {
        return properties.getProperty("HUB_CHANNEL_ID", System.getenv("NEKOFFEE_HUB_CHANNEL_ID"));
    }

    public String getTempChannelCategoryId() {
        return properties.getProperty("TEMP_CHANNEL_CATEGORY_ID", System.getenv("NEKOFFEE_TEMP_CATEGORY_ID"));
    }

    public String getTempChannelNamePrefix() {
        return properties.getProperty("TEMP_CHANNEL_NAME_PREFIX", System.getenv("NEKOFFEE_TEMP_NAME_PREFIX"));
    }

    public Integer getTempChannelUserLimit() {
        return Integer.parseInt(properties.getProperty("TEMP_CHANNEL_USER_LIMIT", System.getenv("NEKOFFEE_TEMP_CHANNEL_USER_LIMIT")));
    }

    public Integer getTempChannelDefaultLock() {
        return Integer.parseInt(properties.getProperty("TEMP_CHANNEL_DEFAULT_LOCKED", System.getenv("NEKOFFEE_TEMP_DEFAULT_LOCKED")));
    }

    public String getCommandPrefix() {
        return properties.getProperty("COMMAND_PREFIX", "!");
    }

    private void loadXPRoleMappings() {
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("XP_ROLE_LEVEL_")) {
                try {
                    int level = Integer.parseInt(key.substring("XP_ROLE_LEVEL_".length()));
                    String roleId = properties.getProperty(key);
                    xpRoleMappings.put(level, roleId);
                    LOGGER.debug("XP Role Mapping: Level {} -> Role ID {}", level, roleId);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Configuração de XP Role inválida: {} não é um número de nível válido.", key);
                }
            }
        }
        LOGGER.info("Carregados {} mapeamentos de XP Role.", xpRoleMappings.size());
    }

    /**
     * Retorna um mapa imutável de Level -> Role ID para XP Roles.
     * As chaves são os níveis a serem alcançados para o cargo.
     */
    public Map<Integer, String> getXPRoleMappings() {
        return Collections.unmodifiableMap(xpRoleMappings);
    }
}