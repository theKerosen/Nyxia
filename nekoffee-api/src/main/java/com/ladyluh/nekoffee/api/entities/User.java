package com.ladyluh.nekoffee.api.entities;

public interface User extends DiscordEntity {

    /**
     * @return O nome de usuário desta pessoa (não o apelido no servidor).
     */
    String getUsername();

    /**
     * @return O discriminador de 4 dígitos do usuário (ex: "0001").
     * Pode ser "0" para usuários com os novos usernames únicos.
     */
    String getDiscriminator();

    /**
     * @return O nome de usuário completo com discriminador (ex: "Nekoffee#0001" ou "nekoffee" se o discriminador for "0").
     */
    String getAsTag();

    /**
     * @return O ID do avatar do usuário. Pode ser null se o usuário não tiver um avatar customizado.
     */
    String getAvatarId();

    /**
     * @return A URL do avatar do usuário. Retorna a URL do avatar padrão se o usuário não tiver um.
     */
    String getEffectiveAvatarUrl();

    /**
     * @return {@code true} se este usuário for um bot, {@code false} caso contrário.
     */
    boolean isBot();

    /**
     * @return {@code true} se este usuário for uma conta oficial do Sistema Discord, {@code false} caso contrário.
     */
    boolean isSystem();

    // Outros métodos como getBannerId(), getAccentColor(), etc. podem ser adicionados depois.
}