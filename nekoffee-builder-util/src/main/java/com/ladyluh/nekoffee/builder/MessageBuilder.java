package com.ladyluh.nekoffee.builder;

// Futuramente: import com.nekoffee.model.message.EmbedImpl;
import com.ladyluh.nekoffee.api.payload.embed.EmbedSendPayload;
import com.ladyluh.nekoffee.api.payload.send.MessageSendPayload;

import java.util.ArrayList;
import java.util.List;

public class MessageBuilder {
    private StringBuilder content;
    private boolean tts = false;
    private List<EmbedSendPayload> embeds = new ArrayList<>();
    // Futuramente: allowed_mentions, components, etc.

    public MessageBuilder() {
        this.content = new StringBuilder();
    }

    public MessageBuilder(String initialContent) {
        this.content = new StringBuilder(initialContent);
    }

    public MessageBuilder setContent(String content) {
        this.content = new StringBuilder(content);
        return this;
    }

    public MessageBuilder appendContent(String text) {
        this.content.append(text);
        return this;
    }

    public MessageBuilder appendContentFormat(String format, Object... args) {
        this.content.append(String.format(format, args));
        return this;
    }

    public MessageBuilder setTTS(boolean tts) {
        this.tts = tts;
        return this;
    }

    public MessageSendPayload build() {
        // Não enviar conteúdo se for vazio e não houver outros elementos (ex: embeds)
        // Por agora, conteúdo é obrigatório se não houver embeds.
        // A API do Discord exige 'content', 'embeds', 'sticker_ids', ou 'files'.
        if (content.isEmpty() && embeds.isEmpty()) {
            throw new IllegalStateException("Message must have content or embeds/stickers/files.");
        }
        if (content.length() > 2000) {
            throw new IllegalArgumentException("Message content cannot exceed 2000 characters.");
        }

        MessageSendPayload payload = new MessageSendPayload();
        payload.setContent(this.content.toString());
        if (this.tts) { // Só envie 'tts: true' se for verdade.
            payload.setTts(this.tts);
        }
        if (!this.embeds.isEmpty()) {
            payload.setEmbeds(this.embeds);
         }
        return payload;
    }

    public MessageBuilder addEmbed(EmbedSendPayload embed) { // Aceita o payload construído
        if (this.embeds.size() >= 10) { // Limite do Discord
            throw new IllegalArgumentException("Cannot add more than 10 embeds to a message.");
        }
        this.embeds.add(embed);
        return this;
    }

    // Método de conveniência para adicionar diretamente de um EmbedBuilder
    public MessageBuilder addEmbed(EmbedBuilder embedBuilder) {
        return addEmbed(embedBuilder.build());
    }

    public String getContent() {
        return content.toString();
    }

    public boolean isTTS() {
        return tts;
    }

    public MessageBuilder setEmbeds(List<EmbedSendPayload> embeds) { // Aceita lista de payloads
        if (embeds.size() > 10) {
            throw new IllegalArgumentException("Cannot have more than 10 embeds in a message.");
        }
        this.embeds = new ArrayList<>(embeds);
        return this;
    }
    

    public boolean isEmpty() {
        return content.isEmpty(); // && embeds.isEmpty(); // Adicionar quando tiver embeds
    }

    // Limpa o builder para reutilização.
    public MessageBuilder clear() {
        this.content.setLength(0);
        this.tts = false;
        // this.embeds.clear();
        return this;
    }
}