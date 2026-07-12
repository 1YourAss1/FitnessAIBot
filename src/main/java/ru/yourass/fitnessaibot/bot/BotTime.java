package ru.yourass.fitnessaibot.bot;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Единый форматтер времени для пользовательских сообщений и серверных логов.
 *
 * <p>Показывает дату/время в часовом поясе JVM (задаётся через POSIX-переменную
 * {@code TZ}, по умолчанию {@code Europe/Moscow}).
 * <p>Пример вывода: {@code 10.07.2026 20:40:34 MSK}.</p>
 */
public final class BotTime {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private BotTime() {
    }

    /**
     * @param ts момент времени или {@code null}
     * @return строка вроде {@code 10.07.2026 20:40:34 MSK} или {@code null},
     *         если на вход пришёл {@code null}
     */
    public static String format(OffsetDateTime ts) {
        return ts == null ? null : FORMATTER.format(ts);
    }
}
