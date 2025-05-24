package com.ladyluh.nekoffee.model.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Member;
import com.ladyluh.nekoffee.api.entities.Role;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;
import com.ladyluh.nekoffee.model.user.UserImpl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MemberImpl extends AbstractDiscordEntity implements Member {


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


    public MemberImpl() {
    }


    public void setGuildId(String guildId) {
        this.guildId = guildId;
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
}