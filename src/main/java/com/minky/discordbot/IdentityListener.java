package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 림버스 컴퍼니 인격 조회
public class IdentityListener extends ListenerAdapter {

    // 위키 수치는 CC BY-SA
    private static final String WIKI_CREDIT = "수치: Limbus Company Wiki (wiki.gg)";

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    static final SlashCommandData COMMAND = Commands.slash("인격", "림버스 컴퍼니 인격의 스킬 · 패시브 조회")
            .addOptions(
                    // 칭호가 길고 한 · 영이 섞여 자동완성 없이는 쓰이지 않는다
                    new OptionData(OptionType.STRING, NAME, "인격 칭호 또는 수감자 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    IdentityListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.identities(), event.getFocusedOption().getValue()).stream()
                .map(identity -> new Command.Choice(label(identity), String.valueOf(identity.id())))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Identity> identities = catalog.identities();
        Identity identity = find(identities, event.getOption(NAME).getAsString());
        if (identity == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(identities.isEmpty() ? "인격 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 인격 · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(embed(identity))
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    // 자동완성 후보를 고르면 값이 인격 ID, 직접 친 글자면 첫 일치
    static Identity find(List<Identity> identities, String query) {
        return identities.stream()
                .filter(identity -> String.valueOf(identity.id()).equals(query))
                .findFirst()
                .or(() -> search(identities, query).stream().findFirst())
                .orElse(null);
    }

    static MessageEmbed embed(Identity identity) {
        EmbedBuilder embed = new EmbedBuilder().setTitle(NoticeListener.cut(title(identity), MessageEmbed.TITLE_MAX_LENGTH));
        Stats stats = identity.stats();
        if (stats != null) {
            List<String> resists = new ArrayList<>();
            addResist(resists, "참격", stats.slash());
            addResist(resists, "관통", stats.pierce());
            addResist(resists, "타격", stats.blunt());
            if (!resists.isEmpty()) {
                embed.setDescription("내성  " + String.join(" · ", resists));
            }
            embed.setFooter(WIKI_CREDIT);
        }

        // 맵으로 모으면 제목이 같은 스킬이 하나로 합쳐진다
        List<Map.Entry<String, String>> fields = new ArrayList<>();
        for (Skill skill : identity.skills()) {
            fields.add(Map.entry(skillHeading(skill), skill.text()));
        }
        for (Passive passive : identity.passives()) {
            String heading = (passive.support() ? "서포트 패시브" : "패시브") + " · " + passive.name();
            fields.add(Map.entry(passive.condition() == null ? heading : heading + " · " + passive.condition(), passive.text()));
        }
        // 필드 1024자 · 임베드 합계 6000자. 넘치는 인격(거미집 계열)은 뒤를 잘라낸다.
        for (Map.Entry<String, String> field : fields) {
            String name = NoticeListener.cut(field.getKey(), MessageEmbed.TITLE_MAX_LENGTH);
            // 디스코드는 빈 필드 값을 거부한다
            String value = field.getValue().isBlank() ? "-" : field.getValue();
            int room = MessageEmbed.EMBED_MAX_LENGTH_BOT - embed.length() - name.length();
            if (room < 2 || embed.getFields().size() == MessageEmbed.MAX_FIELD_AMOUNT) {
                break;
            }
            int limit = Math.min(MessageEmbed.VALUE_MAX_LENGTH, room);
            embed.addField(name, NoticeListener.cut(value, limit), false);
            if (limit < MessageEmbed.VALUE_MAX_LENGTH && value.length() > limit) {
                break;
            }
        }
        return embed.build();
    }

    static List<Identity> search(List<Identity> identities, String query) {
        String needle = compact(query);
        return identities.stream()
                .filter(identity -> compact(identity.title() + identity.sinner()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    static String label(Identity identity) {
        return NoticeListener.cut("[" + identity.title() + "] " + identity.sinner(), OptionData.MAX_CHOICE_NAME_LENGTH);
    }

    private static String title(Identity identity) {
        String title = "[" + identity.title() + "] " + identity.sinner();
        Stats stats = identity.stats();
        if (stats != null && stats.rarity() != null && stats.rarity() > 0) {
            title += " · " + "★".repeat(stats.rarity());
        }
        if (stats != null && stats.season() != null) {
            title += " · 시즌 " + stats.season();
        }
        return title;
    }

    private static void addResist(List<String> resists, String type, Double multiplier) {
        if (multiplier != null) {
            resists.add(type + " ×" + multiplier);
        }
    }

    private static String skillHeading(Skill skill) {
        List<String> parts = new ArrayList<>(List.of(skill.name()));
        SkillStats stats = skill.stats();
        if (stats != null) {
            if (stats.sin() != null) {
                parts.add(stats.sin());
            }
            if (stats.type() != null) {
                parts.add(stats.type());
            }
            if (stats.power() != null) {
                parts.add("위력 " + stats.power());
            }
            if (stats.coinPower() != null) {
                parts.add("코인 " + stats.coinPower() + (stats.coins() == null ? "" : " × " + stats.coins()));
            }
        }
        return String.join(" · ", parts);
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
