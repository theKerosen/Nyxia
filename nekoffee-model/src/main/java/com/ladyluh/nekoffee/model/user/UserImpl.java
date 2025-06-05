package com.ladyluh.nekoffee.model.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.model.AbstractDiscordEntity;

public class UserImpl extends AbstractDiscordEntity implements User {

    @JsonProperty("username")
    private String username;

    @JsonProperty("global_name")
    private String global_name;

    @JsonProperty("discriminator")
    private String discriminator;

    @JsonProperty("avatar")
    private String avatarId;

    @JsonProperty("bot")
    private boolean bot;

    @JsonProperty("system")
    private boolean system;


    public UserImpl() {
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getDiscriminator() {
        return discriminator;
    }

    @Override
    public String getGlobalName() {
        return global_name;
    }

    @Override
    public String getAsTag() {

        return "0".equals(discriminator) ? username : username + "#" + discriminator;
    }

    @Override
    public String getAvatarId() {
        return avatarId;
    }

    @Override
    public String getEffectiveAvatarUrl() {
        if (avatarId != null) {
            String format = avatarId.startsWith("a_") ? "gif" : "png";
            return String.format("https://cdn.discordapp.com/avatars/%s/%s.%s?size=128", getId(), avatarId, format);
        } else {


            int defaultAvatarIndex;
            if ("0".equals(discriminator)) {


                defaultAvatarIndex = (int) ((getIdLong() >> 22) % 6);
            } else {
                defaultAvatarIndex = Integer.parseInt(discriminator) % 5;
            }
            return String.format("https://cdn.discordapp.com/embed/avatars/%d.png", defaultAvatarIndex);
        }
    }

    @Override
    public boolean isBot() {
        return bot;
    }

    @Override
    public boolean isSystem() {
        return system;
    }

    @Override
    public String toString() {
        return "UserImpl{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", global_name='" + global_name + '\'' +
                ", discriminator='" + discriminator + '\'' +
                ", bot=" + bot +
                '}';
    }
}