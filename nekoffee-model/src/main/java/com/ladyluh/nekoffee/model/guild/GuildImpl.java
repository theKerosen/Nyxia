package com.ladyluh.nekoffee.model.guild;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.Guild;
import com.ladyluh.nekoffee.api.entities.Role;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.entities.VoiceState;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;
import com.ladyluh.nekoffee.model.role.RoleImpl;
import com.ladyluh.nekoffee.model.voice.VoiceStateImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GuildImpl extends AbstractDiscordEntity implements Guild {

    @JsonProperty("name")
    private String name;

    @JsonProperty("icon")
    private String iconId;

    @JsonProperty("owner_id")
    private String ownerId;

    @JsonProperty("roles")
    private List<RoleImpl> roles = new ArrayList<>();

    @JsonProperty("voice_states")
    private List<VoiceStateImpl> voiceStates = new ArrayList<>();


    public GuildImpl() {
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getIconId() {
        return iconId;
    }

    @Override
    public String getIconUrl() {
        if (iconId == null) {
            return null;
        }
        String format = iconId.startsWith("a_") ? "gif" : "png";
        return String.format("https://cdn.discordapp.com/icons/%s/%s.%s?size=128", getId(), iconId, format);
    }

    @Override
    public String getOwnerId() {
        return ownerId;
    }

    @Override
    public CompletableFuture<User> retrieveOwner(NekoffeeClient client) {
        if (ownerId == null || client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("OwnerId ou NekoffeeClient não disponível."));
        }
        return client.getUserById(ownerId);
    }


    @Override
    public List<Role> getRoles() {

        return Collections.unmodifiableList(new ArrayList<>(roles));
    }

    @Override
    public CompletableFuture<List<Role>> retrieveRoles(NekoffeeClient client) {
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("NekoffeeClient não disponível."));
        }


        return client.getGuildRoles(getId())
                .thenApply(retrievedRoles -> {


                    return retrievedRoles;
                });
    }


    @Override
    public String toString() {
        return "GuildImpl{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", rolesCount=" + (roles != null ? roles.size() : 0) +
                '}';
    }

    @Override
    public List<VoiceState> getVoiceStates() {
        return Collections.unmodifiableList(new ArrayList<>(voiceStates));
    }
}