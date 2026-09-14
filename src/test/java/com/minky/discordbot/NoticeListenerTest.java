package com.minky.discordbot;

import com.minky.discordbot.NoticeListener.Notice;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoticeListenerTest {

    @Test
    void parseOrdersEventsByPostTime() {
        String json = """
                {"success":1,"events":[
                  {"gid":"706656188498970097","event_name":"시즌 8 : 푼크툼에 대한 안내","appid":1973530,
                   "announcement_body":{"gid":"706656188498970098","posttime":1788774544,"body":"[img]x[/img]","language":4}},
                  {"gid":"705530923042472971","event_name":"2026년 9월 17일 정기 업데이트 안내","appid":1973530,
                   "announcement_body":{"gid":"705530923042472972","posttime":1789378151,"body":"안녕하세요","language":4}},
                  {"gid":"706656188498970095","event_name":"2026년 9월 10일 정기 업데이트 안내","appid":1973530,
                   "announcement_body":{"gid":"706656188498970096","posttime":1788773645,"body":"","language":4}}
                ]}
                """;

        assertEquals(List.of(
                        new Notice("706656188498970095", "2026년 9월 10일 정기 업데이트 안내", 1788773645L, ""),
                        new Notice("706656188498970097", "시즌 8 : 푼크툼에 대한 안내", 1788774544L, "[img]x[/img]"),
                        new Notice("705530923042472971", "2026년 9월 17일 정기 업데이트 안내", 1789378151L, "안녕하세요")),
                NoticeListener.parse(json));
    }

    @Test
    void imagesResolveSteamClanPlaceholderUpToAttachmentLimit() {
        String contents = """
                [img]{STEAM_CLAN_IMAGE}/43587230/f5f2.png[/img]
                [img]{STEAM_CLAN_IMAGE}/43587230/8367.png[/img]""" + "[img]{STEAM_CLAN_IMAGE}/1/a.png[/img]".repeat(10);

        List<String> images = NoticeListener.images(contents);

        assertEquals(10, images.size());
        assertEquals(List.of(
                        "https://clan.akamai.steamstatic.com/images/43587230/f5f2.png",
                        "https://clan.akamai.steamstatic.com/images/43587230/8367.png"),
                images.subList(0, 2));
    }

    @Test
    void videosBecomeYoutubeLinks() {
        assertEquals(List.of("https://youtu.be/h5eR27Gle1U"),
                NoticeListener.videos("[previewyoutube=h5eR27Gle1U;full][/previewyoutube]"));
    }

    @Test
    void embedCarriesTitleLinkAndTextWithoutImages() {
        MessageEmbed embed = NoticeListener.embed(
                new Notice("7", "정기 업데이트 안내", 1L, "[img]{STEAM_CLAN_IMAGE}/1/a.png[/img]안녕하세요"));

        assertEquals("정기 업데이트 안내", embed.getTitle());
        assertEquals("https://store.steampowered.com/news/app/1973530/view/7", embed.getUrl());
        assertEquals("안녕하세요", embed.getDescription());
        assertNull(embed.getImage());
    }

    @Test
    void textStripsSteamTagsButKeepsLiteralBrackets() {
        String contents = """
                [p]안녕하세요, 프로젝트문입니다.[/p][img]{STEAM_CLAN_IMAGE}/1/a.png[/img]



                [b][Hotfix] <변경되는 iOS 권장 사양>[/b]
                [list][*]one[*][url=https://example.com]two[/url][/list]
                [previewyoutube=h5eR27Gle1U;full][/previewyoutube]""";

        assertEquals("""
                안녕하세요, 프로젝트문입니다.

                [Hotfix] <변경되는 iOS 권장 사양>

                • one
                • two""", NoticeListener.text(contents));
    }

    @Test
    void textCutsAtEmbedDescriptionLimit() {
        String text = NoticeListener.text("a".repeat(5000));

        assertEquals(4096, text.length());
        assertEquals('…', text.charAt(4095));
    }
}
