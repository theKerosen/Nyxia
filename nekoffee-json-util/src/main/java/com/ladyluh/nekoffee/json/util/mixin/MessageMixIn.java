package com.ladyluh.nekoffee.json.util.mixin;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ladyluh.nekoffee.model.message.MessageImpl;

/**
 * Jackson MixIn to associate the Message interface with its concrete implementation.
 * When Jackson is asked to deserialize a Message, this tells it to create an instance of MessageImpl.
 */
@JsonDeserialize(as = MessageImpl.class)
public abstract class MessageMixIn {
}