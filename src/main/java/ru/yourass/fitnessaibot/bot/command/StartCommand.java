package ru.yourass.fitnessaibot.bot.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.util.List;

/**
 * Обрабатывает {@code /start} и {@code /help}.
 */
@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class StartCommand implements Command {

    private final UserProfileRepository userProfileRepository;

    public StartCommand(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public String name() {
        return "start";
    }

    @Override
    public List<String> aliases() {
        return List.of("help");
    }

    @Override
    public String description() {
        return "Приветствие и список команд";
    }

    @Override
    public String handle(long chatId) {
        StringBuilder body = new StringBuilder("""
                    ## 👋 Привет! Я фитнес-ассистент.

                    Просто напиши в свободной форме, например:

                    - *«на обед съел макароны с куриной котлетой»*
                    - *«походил на беговой дорожке 30 минут»*
                    - *«я сегодня спал 6 с половиной часов»*
                    - *«вешу 78.4 кг»*

                    Я сам определю тип записи, оценю калории/БЖУ и сохраню.
                    А ещё могу ответить на вопросы по твоему журналу — например: *«сколько калорий я съел за неделю?»*.
                    """);
        if (!userProfileRepository.hasBmrProfile(chatId)) {
            body.append("""

                    > 💡 **Не хватает данных для BMR**
                    >
                    > Расскажи ещё о себе *(пол, возраст, рост, вес)* — я смогу точнее считать калории и BMR/TDEE.
                    """);
        }
        if (!userProfileRepository.hasActivityInProfile(chatId)) {
            body.append("""

                    > 🚶 **Уровень активности**
                    >
                    > Сколько раз в неделю ты тренируешься?
                    > *Малоподвижный / лёгкий (1–3) / умеренный (3–5) / высокий (6–7) / очень высокий* —
                    > без этого BMR/TDEE считается «как для среднего», и норма калорий может врать.
                    """);
        }
        if (!userProfileRepository.hasGoalWeight(chatId)) {
            body.append("""

                    > 🎯 **Цель по весу**
                    >
                    > Если хочешь, можешь задать цель по весу в кг —
                    > тогда я буду видеть динамику «до цели осталось N кг».
                    """);
        }
        body.append("""

                    ## 🔗 Команды Google Health

                    | Команда | Действие |
                    |:--------|:---------|
                    | /google_connect | подключить Google-аккаунт |
                    | /google_disconnect | отвязать Google-аккаунт |
                    | /google_status | статус и время последней синхронизации |
                    | /google_sync | принудительная синхронизация (тянуть новые данные прямо сейчас) |
                    """);
        return body.toString();
    }
}
