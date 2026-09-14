package com.minky.discordbot;

import com.minky.discordbot.MaintenanceAlert.Maintenance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceAlertTest {

    private static final LocalDate SEP_17 = LocalDate.of(2026, 9, 17);

    // tesseract 5.3.4 · -l kor 가 2026년 9월 17일 정기 업데이트 안내 첫 이미지에서 실제로 뽑은 텍스트
    private static final String OCR_SEP_17 = """
            》
            소

             니때버
             배

            '2026년 9월 17일 정기 업데이트 안내

            안녕하세요. 관리자님.

            2026년 9월 17일 06:00 ~ 12:00 (51)에 정기 점검이 진행될 예정입니다.
            점검 일정을 확인하여 게임 이용에 불편이 없으시길 바라며, 데이터 손실을

            ※ 시즌 8 업데이트는 9/17 06:00 ~ 12:00 (<57) 동안 진행될 예정이오니
            """;

    // 2026년 7월 23일 공지의 첫 이미지 — 점검 시각 없는 디렉터 서신
    private static final String OCR_LETTER = "안녕하세요. 프로젝트문 김지훈입니다. 로드맵에서 8월을 목표로 했던 10장 업데이트는 9월 17일로 연기하게 되었습니다.";

    @Test
    void dateReadsOnlyScheduledUpdateTitles() {
        assertEquals(Optional.of(LocalDate.of(2025, 12, 31)), MaintenanceAlert.date("2025년 12월 31일 정기 업데이트 안내"));
        assertEquals(Optional.empty(), MaintenanceAlert.date("2026년 7월 16일 정기 업데이트 이후 알려진 이슈 안내"));
        assertEquals(Optional.empty(), MaintenanceAlert.date("8월 20일 정기 업데이트 공지 내 누락 항목 및 1.112.0 버전 알려진 이슈 안내"));
    }

    @Test
    void windowReadsTimesWrittenForTitleDate() {
        assertEquals(Optional.of(new Maintenance("g", kst(SEP_17, 6), kst(SEP_17, 12))),
                MaintenanceAlert.window("g", SEP_17, OCR_SEP_17));
    }

    @Test
    void windowAcceptsWeekdayInParentheses() {
        assertEquals(Optional.of(new Maintenance("g", kst(SEP_17, 6), kst(SEP_17, 12))),
                MaintenanceAlert.window("g", SEP_17, "9월 17일(목) 06:00 ~ 12:00"));
    }

    @Test
    void windowIgnoresTimesOfOtherDates() {
        assertEquals(Optional.empty(), MaintenanceAlert.window("g", SEP_17, "2026년 9월 10일 10:00 ~ 12:00 (KST)에 정기 점검"));
        assertEquals(Optional.empty(), MaintenanceAlert.window("g", SEP_17, "2026년 9월 17일 25:00 ~ 12:00"));
    }

    @Test
    void resolveStopsAtFirstImageWithTimes() {
        AtomicInteger read = new AtomicInteger();

        Optional<Maintenance> maintenance = MaintenanceAlert.resolve("g", SEP_17,
                Stream.of(OCR_LETTER, OCR_SEP_17, "2026년 9월 17일 07:00 ~ 13:00").peek(text -> read.incrementAndGet()));

        assertEquals(Optional.of(new Maintenance("g", kst(SEP_17, 6), kst(SEP_17, 12))), maintenance);
        assertEquals(2, read.get());
    }

    @Test
    void resolveFindsNothingWithoutTimes() {
        assertEquals(Optional.empty(), MaintenanceAlert.resolve("g", SEP_17, Stream.of(OCR_LETTER, "")));
        assertEquals(new Maintenance("g", kst(SEP_17, 10), kst(SEP_17, 12)), MaintenanceAlert.usual("g", SEP_17));
    }

    private static java.time.Instant kst(LocalDate day, int hour) {
        return LocalDateTime.of(day.getYear(), day.getMonth(), day.getDayOfMonth(), hour, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }
}
