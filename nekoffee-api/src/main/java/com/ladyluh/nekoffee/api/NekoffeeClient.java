package com.ladyluh.nekoffee.api;

import com.ladyluh.nekoffee.api.entities.*;
import com.ladyluh.nekoffee.api.entities.channel.Channel;
import com.ladyluh.nekoffee.api.entities.channel.ChannelType;
import com.ladyluh.nekoffee.api.event.EventListener;
import com.ladyluh.nekoffee.api.gateway.GatewayIntent;
import com.ladyluh.nekoffee.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NekoffeeClient {

    User getSelfUser();

    /**
     * Inicia a conexão com o Discord e autentica o bot.
     * Este método deve ser chamado antes de qualquer outra interação com o Discord.
     *
     * @param token O token do bot.
     * @return Um CompletableFuture que é completado quando o login (e conexão com o gateway, futuramente) é bem-sucedido.
     */
    CompletableFuture<Void> login(String token, Collection<GatewayIntent> intents);

    /**
     * Desconecta o bot do Discord.
     */
    void shutdown();

    /**
     * Envia uma mensagem para um canal específico.
     *
     * @param channelId O ID do canal.
     * @param content   O conteúdo da mensagem.
     * @return Um CompletableFuture contendo a Mensagem enviada, se bem-sucedido.
     */
    CompletableFuture<Message> sendMessage(String channelId, String content);

    /**
     * Registra um ouvinte de eventos.
     *
     * @param listener O ouvinte a ser registrado.
     */
    void addEventListener(EventListener listener);

    /**
     * Remove um ouvinte de eventos registrado.
     *
     * @param listener O ouvinte a ser removido.
     */
    void removeEventListener(EventListener listener);

    /**
     * Busca um usuário pelo seu ID.
     *
     * @param userId O ID do usuário.
     * @return Um CompletableFuture contendo o objeto User, se encontrado.
     */
    CompletableFuture<User> getUserById(String userId);

    /**
     * Busca um canal pelo seu ID.
     *
     * @param channelId O ID do canal.
     * @return Um CompletableFuture contendo o objeto Channel, se encontrado.
     * O tipo específico do canal (TextChannel, VoiceChannel, etc.) precisará ser
     * verificado e possivelmente "castado" pelo chamador.
     */
    CompletableFuture<Channel> getChannelById(String channelId);

    /**
     * Busca um servidor (Guild) pelo seu ID.
     *
     * @param guildId O ID do servidor.
     * @return Um CompletableFuture contendo o objeto Guild, se encontrado.
     */
    CompletableFuture<Guild> getGuildById(String guildId);

    CompletableFuture<List<Role>> getGuildRoles(String guildId);

    /**
     * Busca um membro específico de um servidor pelo ID do servidor e ID do usuário.
     *
     * @param guildId O ID do servidor.
     * @param userId  O ID do usuário (que também é o ID do membro).
     * @return Um CompletableFuture contendo o objeto Member, se encontrado.
     */
    CompletableFuture<Member> getGuildMember(String guildId, String userId);

    /**
     * Adiciona um cargo a um membro específico em um servidor.
     * Requer a permissão MANAGE_ROLES.
     *
     * @param guildId O ID do servidor.
     * @param userId  O ID do usuário (membro).
     * @param roleId  O ID do cargo a ser adicionado.
     * @return Um CompletableFuture que é completado quando a operação é bem-sucedida, ou com uma exceção em caso de falha.
     */
    CompletableFuture<Void> addRoleToMember(String guildId, String userId, String roleId);

    /**
     * Remove um cargo de um membro específico em um servidor.
     * Requer a permissão MANAGE_ROLES.
     *
     * @param guildId O ID do servidor.
     * @param userId  O ID do usuário (membro).
     * @param roleId  O ID do cargo a ser removido.
     * @return Um CompletableFuture que é completado quando a operação é bem-sucedida, ou com uma exceção em caso de falha.
     */
    CompletableFuture<Void> removeRoleFromMember(String guildId, String userId, String roleId);

    CompletableFuture<Message> sendMessage(String channelId, MessageSendPayload messageData);

    /**
     * Cria um novo canal em um servidor.
     * Requer a permissão MANAGE_CHANNELS.
     *
     * @param guildId          O ID do servidor.
     * @param name             O nome do novo canal.
     * @param type             O tipo do canal a ser criado (ex: GUILD_VOICE, GUILD_TEXT).
     * @param parentCategoryId (Opcional) O ID da categoria onde o canal será criado. Pode ser null.
     * @return Um CompletableFuture contendo o objeto Channel criado.
     */
    CompletableFuture<Channel> createGuildChannel(String guildId, String name, ChannelType type, @Nullable String parentCategoryId);

    /**
     * Deleta um canal.
     * Requer a permissão MANAGE_CHANNELS no canal pai (para canais de guild) ou no próprio canal.
     *
     * @param channelId O ID do canal a ser deletado.
     * @return Um CompletableFuture que é completado quando a operação é bem-sucedida, ou com uma exceção.
     * Pode retornar o objeto Channel deletado em algumas implementações de API, ou Void. Vamos retornar Channel.
     */
    CompletableFuture<Channel> deleteChannel(String channelId);

    /**
     * Modifica um membro da guild, usado aqui para mover entre canais de voz.
     * Requer a permissão MOVE_MEMBERS.
     *
     * @param guildId        O ID do servidor.
     * @param userId         O ID do usuário/membro.
     * @param voiceChannelId O ID do canal de voz para onde mover o membro. Passe null para desconectar.
     * @return Um CompletableFuture que completa quando a operação é bem-sucedida.
     */
    CompletableFuture<Void> modifyGuildMemberVoiceChannel(String guildId, String userId, @Nullable String voiceChannelId);

    /**
     * Modifica as configurações de um canal existente.
     * Requer permissões apropriadas (ex: MANAGE_CHANNELS).
     *
     * @param channelId O ID do canal a ser modificado.
     * @param payload   O payload contendo as alterações.
     * @return Um CompletableFuture contendo o objeto Channel atualizado.
     */
    CompletableFuture<Channel> modifyChannel(String channelId, ChannelModifyPayload payload);

    CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type, Collection<Permission> allow, Collection<Permission> deny);


    CompletableFuture<Void> editChannelPermissions(String channelId, String targetId, TargetType type,
                                                   long allowBitmask, long denyBitmask);
}