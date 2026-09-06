package com.mario.rl.discordbot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PingPongListener extends ListenerAdapter {

    private static final String PING = "!ping";
    private static final String PONG = "Pong!";

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (!PING.equals(event.getMessage().getContentRaw())) {
            return;
        }
        event.getChannel().sendMessage(PONG).queue();
    }
}
