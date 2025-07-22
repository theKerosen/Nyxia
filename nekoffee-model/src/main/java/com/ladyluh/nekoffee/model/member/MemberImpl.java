package com.ladyluh.nekoffee.model.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.entities.Role;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.payload.permission.Permission;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;
import com.ladyluh.nekoffee.model.user.UserImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MemberImpl extends AbstractDiscordEntity implements Member {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemberImpl.class);

    @JsonProperty("user")
    private UserImpl user;
    @JsonProperty("nick")
    private String nickname;
    @JsonProperty("roles")
    private List<String> roleIds = new ArrayList<>();
    @JsonProperty("joined_at")
    private OffsetDateTime joinedAt;
    @JsonProperty("premium_since")
    private OffsetDateTime premiumSince;
    @JsonProperty("deaf")
    private boolean deafened;
    @JsonProperty("mute")
    private boolean muted;
    @JsonProperty("guild_id")
    private String guildId;

    @JsonIgnore
    private NekoffeeClient nekoffeeClient;

    public MemberImpl() {
    }

    public void setNekoffeeClient(NekoffeeClient client) {
        this.nekoffeeClient = Objects.requireNonNull(client, "NekoffeeClient cannot be null for MemberImpl.");
    }

    @Override
    public String getId() {
        return user != null ? user.getId() : super.getId();
    }

    @Override
    public long getIdLong() {
        return user != null ? user.getIdLong() : super.getIdLong();
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public String getEffectiveName() {
        return nickname != null ? nickname : (user != null ? user.getUsername() : "Unknown User");
    }

    @Override
    public List<String> getRoleIds() {
        return Collections.unmodifiableList(roleIds);
    }

    @Override
    public CompletableFuture<List<Role>> getRoles(NekoffeeClient client) {
        if (guildId == null || client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("GuildId ou NekoffeeClient não disponível."));
        }
        return client.getGuildRoles(guildId).thenApply(guildRoles ->
                guildRoles.stream()
                        .filter(role -> roleIds.contains(role.getId()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public OffsetDateTime getTimeJoined() {
        return joinedAt;
    }

    @Override
    public OffsetDateTime getTimeBoosted() {
        return premiumSince;
    }

    @Override
    public boolean isDeafened() {
        return deafened;
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    @Override
    public CompletableFuture<Guild> retrieveGuild(NekoffeeClient client) {
        if (guildId == null || client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("GuildId ou NekoffeeClient não disponível."));
        }
        return client.getGuildById(guildId);
    }

    @Override
    public String toString() {
        return "MemberImpl{" +
                "userId=" + getId() +
                ", effectiveName='" + getEffectiveName() + '\'' +
                ", nickname='" + nickname + '\'' +
                ", guildId='" + guildId + '\'' +
                ", rolesCount=" + roleIds.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemberImpl member = (MemberImpl) o;

        return Objects.equals(getId(), member.getId()) && Objects.equals(guildId, member.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), guildId);
    }

    @Override
    public CompletableFuture<Long> getPermissionsRaw() {
        if (nekoffeeClient == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("NekoffeeClient não está setado para MemberImpl."));
        }
        if (guildId == null) {
            return CompletableFuture.completedFuture(0L);
        }

        return nekoffeeClient.getGuildRoles(guildId)
                .thenApply(allGuildRoles -> {
                    long calculatedPermissions = 0L;

                    Optional<Role> everyoneRoleOpt = allGuildRoles.stream()
                            .filter(role -> role.getId().equals(guildId))
                            .findFirst();

                    if (everyoneRoleOpt.isPresent()) {
                        calculatedPermissions |= everyoneRoleOpt.get().getPermissionsRaw();
                    } else {
                        LOGGER.warn("Cargo @everyone não encontrado para guild {}. Permissões base podem estar incorretas.", guildId);
                    }

                    for (String roleId : roleIds) {
                        Optional<Role> memberRoleOpt = allGuildRoles.stream()
                                .filter(role -> role.getId().equals(roleId))
                                .findFirst();
                        if (memberRoleOpt.isPresent()) {
                            calculatedPermissions |= memberRoleOpt.get().getPermissionsRaw();
                        }
                    }

                    if ((calculatedPermissions & Permission.ADMINISTRATOR.getRawValue()) == Permission.ADMINISTRATOR.getRawValue()) {
                        return Permission.ADMINISTRATOR.getRawValue();
                    }

                    return calculatedPermissions;
                })
                .exceptionally(ex -> {
                    LOGGER.error("Falha ao derivar permissões para o membro {} na guild {}:", getId(), guildId, ex);
                    return 0L;
                });
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(Permission permission) {
        return getPermissionsRaw().thenApply(rawPermissions -> {

            if ((rawPermissions & Permission.ADMINISTRATOR.getRawValue()) == Permission.ADMINISTRATOR.getRawValue()) {
                return true;
            }
            return (rawPermissions & permission.getRawValue()) == permission.getRawValue();
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPermissions(Collection<Permission> permissions) {
        return getPermissionsRaw().thenApply(rawPermissions -> {
            if ((rawPermissions & Permission.ADMINISTRATOR.getRawValue()) == Permission.ADMINISTRATOR.getRawValue()) {
                return true;
            }
            long requiredPermissions = Permission.calculateBitmask(permissions);
            return (rawPermissions & requiredPermissions) == requiredPermissions;
        });
    }
}