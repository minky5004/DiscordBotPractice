package com.mario.rl.discordbot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class PingPongListener extends ListenerAdapter {

    static final SlashCommandData COMMAND = Commands.slash("ping", "봇이 살아 있는지 확인");

    private static final String PONG = "Pong!";

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.reply(PONG).queue();
    }
}
