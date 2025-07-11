package com.ladyluh.nekoffee.api.payload.send;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ladyluh.nekoffee.api.payload.embed.EmbedSendPayload;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSendPayload {

    @JsonProperty("content")
    private String content;

    @JsonProperty("tts")
    private Boolean tts;
    private List<EmbedSendPayload> embeds;


    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getTts() {
        return tts;
    }

    public void setTts(Boolean tts) {
        this.tts = tts;
    }

    public List<EmbedSendPayload> getEmbeds() {
        return embeds;
    }

    public void setEmbeds(List<EmbedSendPayload> embeds) {
        this.embeds = embeds;
    }
}