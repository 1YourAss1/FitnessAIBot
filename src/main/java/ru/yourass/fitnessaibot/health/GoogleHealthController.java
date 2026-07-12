package ru.yourass.fitnessaibot.health;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yourass.fitnessaibot.config.BotProperties;

import java.io.IOException;

/**
 * HTTP-контроллер для OAuth-flow Google Health. Ссылку пользователь
 * получает в Telegram-боте через {@code /connect_google}, callback
 * обрабатывает Google, дальше пользователь возвращается в бот.
 */
@Controller
@RequestMapping("/google-health")
public class GoogleHealthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleHealthController.class);

    private final GoogleHealthOAuthService oauth;
    private final BotProperties botProperties;

    public GoogleHealthController(GoogleHealthOAuthService oauth, BotProperties botProperties) {
        this.oauth = oauth;
        this.botProperties = botProperties;
    }

    /**
     * Callback от Google после OAuth consent. Сохраняет credential в БД
     * и показывает пользователю короткое текстовое сообщение — он сам
     * вернётся в Telegram-бот.
     */
    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        if (error != null && !error.isBlank()) {
            log.warn("Google OAuth: error from Google: {}", error);
            writeResult(response, false, "Google отклонил авторизацию: " + error);
            return;
        }
        Long telegramUserId = parseUserId(state);
        if (telegramUserId == null) {
            log.warn("Google OAuth: bad state={}", state);
            writeResult(response, false,
                    "Сессия подключения истекла. Введите /connect_google ещё раз.");
            return;
        }
        if (code == null || code.isBlank()) {
            writeResult(response, false, "Google не вернул код авторизации.");
            return;
        }
        try {
            oauth.handleCallback(telegramUserId, code);
            log.info("Google OAuth: connected user={}", telegramUserId);
            writeResult(response, true,
                    "Google-аккаунт подключён. Вернитесь в Telegram-бот — "
                            + "данные будут подтягиваться автоматически.");
        } catch (Exception ex) {
            log.error("Google OAuth: token exchange failed for user={}", telegramUserId, ex);
            writeResult(response, false,
                    "Не удалось подключить Google-аккаунт: " + ex.getMessage());
        }
    }

    private static Long parseUserId(String state) {
        if (state == null || !state.startsWith(GoogleHealthCredentialStore.KEY_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(state.substring(GoogleHealthCredentialStore.KEY_PREFIX.length()));
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private void writeResult(HttpServletResponse response, boolean ok, String message)
            throws IOException {
        String title = ok ? "✅ Готово" : "⚠️ Ошибка";
        String channelUrl = botProperties.url();
        boolean hasChannel = channelUrl != null && !channelUrl.isBlank();
        String channelLink = hasChannel
                ? "<p><a href=\"" + escapeHtml(channelUrl) + "\" target=\"_blank\" rel=\"noopener\">"
                + "Вернуться в Telegram</a></p>"
                : "";
        String html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>%s</title></head>
                <body style="font-family:sans-serif;text-align:center;padding:32px;">
                <h1>%s</h1>
                <p>%s</p>
                %s
                </body></html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message), channelLink);
        response.getWriter().write(html);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
