package ru.yourass.fitnessaibot.bot.command;

import java.util.List;

/**
 * Обработчик команды Telegram-бота.
 */
public interface Command {

    /** Основное имя команды без ведущего слеша, например {@code "start"}. */
    String name();

    /** Дополнительные имена без ведущего слеша, по умолчанию — пусто. */
    default List<String> aliases() {
        return List.of();
    }

    /** Описание в меню Telegram. */
    String description();

    /** Выполнить команду для указанного чата. */
    String handle(long chatId);
}
