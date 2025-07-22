package com.ladyluh.nekoffee.model.gateway;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.api.entities.User;
import com.ladyluh.nekoffee.api.event.AbstractEvent;

public class ReadyEvent extends AbstractEvent {
    private final User selfUser;
    private final String sessionId;
    private final String resumeGatewayUrl;
    private final int gatewayVersion;

    public ReadyEvent(NekoffeeClient nekoffeeClient, User selfUser, String sessionId, String resumeGatewayUrl, int gatewayVersion /*, List<ReadyPayloadData.UnavailableGuild> unavailableGuilds */) {
        super(nekoffeeClient);
        this.selfUser = selfUser;
        this.sessionId = sessionId;
        this.resumeGatewayUrl = resumeGatewayUrl;
        this.gatewayVersion = gatewayVersion;

    }

    public User getSelfUser() {
        return selfUser;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getResumeGatewayUrl() {
        return resumeGatewayUrl;
    }

    public int getGatewayVersion() {
        return gatewayVersion;
    }

}