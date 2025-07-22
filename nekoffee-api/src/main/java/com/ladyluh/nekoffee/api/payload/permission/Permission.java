package com.ladyluh.nekoffee.api.payload.permission;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Representa as permissões do Discord.
 * Os valores são baseados nos bitwise flags da API do Discord.
 * Veja: <a href="https://discord.com/developers/docs/topics/permissions#permissions-bitwise-permission-flags">...</a>
 */
public enum Permission {

    CREATE_INSTANT_INVITE(1L),
    KICK_MEMBERS(1L << 1),
    BAN_MEMBERS(1L << 2),
    ADMINISTRATOR(1L << 3),
    MANAGE_CHANNELS(1L << 4),
    MANAGE_GUILD(1L << 5),
    ADD_REACTIONS(1L << 6),
    VIEW_AUDIT_LOG(1L << 7),
    PRIORITY_SPEAKER(1L << 8),
    STREAM(1L << 9),
    VIEW_CHANNEL(1L << 10),
    SEND_MESSAGES(1L << 11),
    SEND_TTS_MESSAGES(1L << 12),
    MANAGE_MESSAGES(1L << 13),
    EMBED_LINKS(1L << 14),
    ATTACH_FILES(1L << 15),
    READ_MESSAGE_HISTORY(1L << 16),
    MENTION_EVERYONE(1L << 17),
    USE_EXTERNAL_EMOJIS(1L << 18),
    VIEW_GUILD_INSIGHTS(1L << 19),

    CONNECT(1L << 20),
    SPEAK(1L << 21),
    MUTE_MEMBERS(1L << 22),
    DEAFEN_MEMBERS(1L << 23),
    MOVE_MEMBERS(1L << 24),
    USE_VAD(1L << 25),
    CHANGE_NICKNAME(1L << 26),
    MANAGE_NICKNAMES(1L << 27),
    MANAGE_ROLES(1L << 28),
    MANAGE_WEBHOOKS(1L << 29),
    MANAGE_GUILD_EXPRESSIONS(1L << 30),
    USE_APPLICATION_COMMANDS(1L << 31),// Permite usar comandos de aplicação (comandos slash)
    REQUEST_TO_SPEAK(1L << 32),
    MANAGE_EVENTS(1L << 33),
    MANAGE_THREADS(1L << 34),
    CREATE_PUBLIC_THREADS(1L << 35),
    CREATE_PRIVATE_THREADS(1L << 36),// Permite criar tópicos privados
    USE_EXTERNAL_STICKERS(1L << 37),
    SEND_MESSAGES_IN_THREADS(1L << 38),// Permite enviar mensagens em tópicos
    USE_EMBEDDED_ACTIVITIES(1L << 39),
    MODERATE_MEMBERS(1L << 40),
    VIEW_CREATOR_MONETIZATION_ANALYTICS(1L << 41),
    USE_SOUNDBOARD(1L << 42),

    SEND_VOICE_MESSAGES(1L << 46);

    private final long value;

    Permission(long value) {
        this.value = value;
    }

    /**
     * @return O valor raw (bitwise) desta permissão.
     */
    public long getRawValue() {
        return value;
    }

    /**
     * Calcula o bitmask combinado para uma coleção de permissões.
     *
     * @param permissions A coleção de permissões.
     * @return O bitmask combinado.
     */
    public static long calculateBitmask(Collection<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0L;
        }
        long bitmask = 0L;
        for (Permission perm : permissions) {
            if (perm != null) {
                bitmask |= perm.getRawValue();
            }
        }
        return bitmask;
    }

    /**
     * Cria um EnumSet de permissões a partir de um bitmask raw.
     *
     * @param rawPermissions O bitmask raw.
     * @return Um EnumSet contendo as permissões representadas pelo bitmask.
     */
    public static EnumSet<Permission> getPermissions(long rawPermissions) {
        EnumSet<Permission> perms = EnumSet.noneOf(Permission.class);
        for (Permission p : values()) {
            if ((rawPermissions & p.getRawValue()) == p.getRawValue()) {
                perms.add(p);
            }
        }
        return perms;
    }

    /**
     * Verifica se este bitmask de permissão contém todas as permissões especificadas.
     *
     * @param currentBitmask     O bitmask atual.
     * @param permissionsToCheck As permissões a serem verificadas.
     * @return true se todas as permissões especificadas estiverem presentes no bitmask.
     */
    public static boolean hasPermissions(long currentBitmask, Collection<Permission> permissionsToCheck) {
        if (permissionsToCheck == null || permissionsToCheck.isEmpty()) {
            return true;
        }
        for (Permission perm : permissionsToCheck) {
            if (!hasPermission(currentBitmask, perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifica se este bitmask de permissão contém a permissão especificada.
     *
     * @param currentBitmask    O bitmask atual.
     * @param permissionToCheck A permissão a ser verificada.
     * @return true se a permissão especificada estiver presente no bitmask.
     */
    public static boolean hasPermission(long currentBitmask, Permission permissionToCheck) {
        if (permissionToCheck == null) return true;
        return (currentBitmask & permissionToCheck.getRawValue()) == permissionToCheck.getRawValue();
    }
}