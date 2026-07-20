package com.openadb.adb.bot;

import com.openadb.adb.config.TelegramBotProperties;
import com.openadb.adb.entity.AccessRecord;
import com.openadb.adb.entity.ActivationLog;
import com.openadb.adb.entity.BotUser;
import com.openadb.adb.repository.AccessRecordRepository;
import com.openadb.adb.repository.ActivationLogRepository;
import com.openadb.adb.repository.BotUserRepository;
import com.openadb.adb.service.AuthService;
import com.openadb.adb.service.SettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivationBot extends TelegramLongPollingBot {

    private static final String BTN_GENERATE = "🔧 Генерация Номера и Активация синего экрана";
    private static final String BTN_INSTRUCTION = "📖 Инструкция";

    private static final String BTN_ADMIN_USERS = "👥 Пользователи";
    private static final String BTN_ADMIN_ADD_USER = "➕ Добавить пользователя";
    private static final String BTN_ADMIN_DELETE_USER = "🗑 Удалить пользователя";
    private static final String BTN_ADMIN_VINS = "📋 Активные VIN";
    private static final String BTN_ADMIN_DELETE_VIN = "❌ Удалить VIN";
    private static final String BTN_ADMIN_LOGS = "📊 Журнал действий";
    private static final String BTN_ADMIN_SET_LIMIT = "🎯 Выдать активации";
    private static final String BTN_ADMIN_SET_TTL = "⏱ Время активации";
    private static final String BTN_CANCEL = "↩️ Отмена";

    private static final String STATE_WAITING_VIN = "WAITING_VIN";
    private static final String STATE_ADMIN_ADD_USER = "ADMIN_ADD_USER";
    private static final String STATE_ADMIN_DELETE_USER = "ADMIN_DELETE_USER";
    private static final String STATE_ADMIN_DELETE_VIN = "ADMIN_DELETE_VIN";
    private static final String STATE_ADMIN_LIMIT_ID = "ADMIN_LIMIT_ID";
    private static final String STATE_ADMIN_LIMIT_COUNT = "ADMIN_LIMIT_COUNT";
    private static final String STATE_ADMIN_TTL = "ADMIN_TTL";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss");

    private static final String INSTRUCTION_TEXT = """
            📖 *Инструкция*

            1. Нажмите кнопку «🔧 Генерация Номера и Активация синего экрана».
            2. Отправьте VIN автомобиля (ровно 17 символов, буквы латинские).
            3. Бот сгенерирует factory-код формата `*#…#*` и активирует «синий экран» для этого VIN.
            4. После активации сервер на запрос `authQuery` будет отвечать `success` для этого VIN *в течение времени, заданного администратором* (по умолчанию 24 ч).
            5. По истечении срока активация истекает — `authQuery` начнёт возвращать ошибку. Повторная активация того же VIN продлевает срок ещё на тот же период.

            ⚠️ Каждая активация расходует одну попытку из вашего лимита.
            Лимит выдаёт администратор. У администратора попытки не расходуются.
            """;

    private final TelegramBotProperties props;
    private final BotUserRepository userRepository;
    private final AccessRecordRepository accessRepository;
    private final ActivationLogRepository logRepository;
    private final SettingsService settingsService;

    private final ConcurrentMap<Long, String> chatState = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> pendingTargetId = new ConcurrentHashMap<>();

    @PostConstruct
    public void register() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(this);
            log.info("Telegram bot @{} registered", props.getUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register telegram bot", e);
        }
    }

    @Override
    public String getBotUsername() {
        return props.getUsername();
    }

    @Override
    public String getBotToken() {
        return props.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        User from = message.getFrom();
        Long chatId = message.getChatId();
        String text = message.getText().trim();

        BotUser user = touchUser(from);

        try {
            handle(chatId, user, text);
        } catch (Exception e) {
            log.error("Bot handling error", e);
            safeSend(chatId, "⚠️ Внутренняя ошибка. Попробуйте позже.");
        }
    }

    private void handle(Long chatId, BotUser user, String text) {
        if (text.equalsIgnoreCase("/cancel") || text.equals(BTN_CANCEL)) {
            chatState.remove(chatId);
            pendingTargetId.remove(chatId);
            safeSend(chatId, "Операция отменена.");
            sendWelcome(chatId, user);
            return;
        }

        if (text.equals("/start")) {
            chatState.remove(chatId);
            sendWelcome(chatId, user);
            return;
        }

        if (text.equals(BTN_INSTRUCTION) || text.equalsIgnoreCase("/help")) {
            chatState.remove(chatId);
            sendInstruction(chatId, user);
            return;
        }

        if (text.equals(BTN_GENERATE)) {
            if (!isAdmin(user) && user.getActivationsRemaining() <= 0) {
                logAction(user, null, "GENERATE_REQUEST", false, "no activations left");
                safeSend(chatId, "❌ У вас нет активаций. Обратитесь к администратору.\n"
                        + "Ваш Telegram ID: `" + user.getTelegramId() + "`", true);
                return;
            }
            chatState.put(chatId, STATE_WAITING_VIN);
            String left = isAdmin(user) ? "∞" : String.valueOf(user.getActivationsRemaining());
            promptWithCancel(chatId, "Отправьте VIN автомобиля (17 символов). Осталось активаций: " + left);
            return;
        }

        if (isAdmin(user) && handleAdminButton(chatId, user, text)) {
            return;
        }

        String state = chatState.get(chatId);
        if (state != null) {
            chatState.remove(chatId);
            handleState(chatId, user, state, text);
            return;
        }

        sendWelcome(chatId, user);
    }

    private boolean handleAdminButton(Long chatId, BotUser user, String text) {
        switch (text) {
            case BTN_ADMIN_USERS -> {
                sendUsersList(chatId, user);
                return true;
            }
            case BTN_ADMIN_SET_TTL -> {
                chatState.put(chatId, STATE_ADMIN_TTL);
                int current = settingsService.getActivationTtlHours();
                promptWithCancel(chatId, "Сейчас время активации: *" + current + " ч*.\n"
                        + "Отправьте новое значение в часах (целое число ≥ 1).");
                return true;
            }
            case BTN_ADMIN_ADD_USER -> {
                chatState.put(chatId, STATE_ADMIN_ADD_USER);
                promptWithCancel(chatId, "Отправьте Telegram ID пользователя, которого нужно добавить с доступом.\n"
                        + "(Пользователь появится в БД, даже если ещё не открывал бота.)");
                return true;
            }
            case BTN_ADMIN_DELETE_USER -> {
                chatState.put(chatId, STATE_ADMIN_DELETE_USER);
                promptWithCancel(chatId, "Отправьте Telegram ID пользователя, которого нужно удалить из БД.");
                return true;
            }
            case BTN_ADMIN_VINS -> {
                sendVinList(chatId, user);
                return true;
            }
            case BTN_ADMIN_DELETE_VIN -> {
                chatState.put(chatId, STATE_ADMIN_DELETE_VIN);
                promptWithCancel(chatId, "Отправьте VIN, который нужно удалить из активных.");
                return true;
            }
            case BTN_ADMIN_LOGS -> {
                sendLogs(chatId, user);
                return true;
            }
            case BTN_ADMIN_SET_LIMIT -> {
                chatState.put(chatId, STATE_ADMIN_LIMIT_ID);
                promptWithCancel(chatId, "Отправьте Telegram ID пользователя, которому нужно выдать активации.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handleState(Long chatId, BotUser user, String state, String text) {
        switch (state) {
            case STATE_WAITING_VIN -> processVin(chatId, user, text);
            case STATE_ADMIN_ADD_USER -> adminAddUser(chatId, user, text);
            case STATE_ADMIN_DELETE_USER -> adminDeleteUser(chatId, user, text);
            case STATE_ADMIN_DELETE_VIN -> adminDeleteVin(chatId, user, text);
            case STATE_ADMIN_LIMIT_ID -> adminLimitCaptureId(chatId, text);
            case STATE_ADMIN_LIMIT_COUNT -> adminLimitApply(chatId, user, text);
            case STATE_ADMIN_TTL -> adminSetTtl(chatId, user, text);
            default -> sendWelcome(chatId, user);
        }
    }

    private void processVin(Long chatId, BotUser user, String rawVin) {
        String vin = rawVin.trim().toUpperCase();

        if (vin.length() != 17) {
            logAction(user, vin, "ACTIVATE", false, "invalid vin length");
            safeSend(chatId, "❌ VIN должен содержать ровно 17 символов. Получено: " + vin.length());
            return;
        }

        String factoryCode = AuthService.vinToFactoryCode(vin);
        if (factoryCode == null) {
            logAction(user, vin, "ACTIVATE", false, "vin contains invalid characters");
            safeSend(chatId, "❌ VIN содержит недопустимые символы.");
            return;
        }

        if (!isAdmin(user) && user.getActivationsRemaining() <= 0) {
            logAction(user, vin, "ACTIVATE", false, "no activations left");
            safeSend(chatId, "❌ У вас нет активаций. Обратитесь к администратору.");
            return;
        }

        int ttlHours = settingsService.getActivationTtlHours();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(ttlHours);

        Optional<AccessRecord> existing = accessRepository.findByVin(vin);
        AccessRecord record = existing.orElseGet(() -> AccessRecord.builder()
                .vin(vin)
                .telegramId(user.getTelegramId())
                .activatedAt(now)
                .build());
        record.setFactoryCode(factoryCode);
        record.setTelegramId(user.getTelegramId());
        record.setActivatedAt(now);
        record.setExpiresAt(expiresAt);
        accessRepository.save(record);

        int left;
        if (isAdmin(user)) {
            left = -1;
        } else {
            user.setActivationsRemaining(user.getActivationsRemaining() - 1);
            userRepository.save(user);
            left = user.getActivationsRemaining();
        }

        logAction(user, vin, "ACTIVATE", true,
                "expires " + expiresAt.format(TS) + (left >= 0 ? ", left=" + left : ""));

        String leftText = left < 0 ? "∞ (админ)" : String.valueOf(left);
        String reply = """
                ✅ *Активация выполнена*

                VIN: `%s`
                Factory-код: `%s`
                Действует до: *%s* (%d ч)
                Осталось активаций: *%s*
                """.formatted(vin, factoryCode, expiresAt.format(TS), ttlHours, leftText);
        safeSend(chatId, reply, true);
    }

    private void adminLimitCaptureId(Long chatId, String text) {
        Long targetId = parseLongOrNull(text);
        if (targetId == null) {
            safeSend(chatId, "❌ Некорректный Telegram ID. Ожидается число.");
            return;
        }
        pendingTargetId.put(chatId, targetId);
        chatState.put(chatId, STATE_ADMIN_LIMIT_COUNT);
        promptWithCancel(chatId, "Сколько активаций выдать пользователю " + targetId
                + "? (Отправьте число. Значение заменит текущий лимит; 0 — обнулить.)");
    }

    private void adminLimitApply(Long chatId, BotUser admin, String text) {
        Long targetId = pendingTargetId.remove(chatId);
        if (targetId == null) {
            safeSend(chatId, "❌ Пользователь не выбран. Начните заново.");
            return;
        }
        Integer count = parseIntOrNull(text);
        if (count == null || count < 0) {
            safeSend(chatId, "❌ Ожидается неотрицательное число.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        BotUser u = userRepository.findById(targetId).orElseGet(() -> BotUser.builder()
                .telegramId(targetId)
                .hasAccess(true)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        u.setActivationsRemaining(count);
        if (count > 0) u.setHasAccess(true);
        if (u.getFirstSeenAt() == null) u.setFirstSeenAt(now);
        if (u.getLastSeenAt() == null) u.setLastSeenAt(now);
        userRepository.save(u);
        logAction(admin, null, "SET_LIMIT", true, "target=" + targetId + ", count=" + count);
        safeSend(chatId, "✅ Пользователю " + describeUser(u) + " выдано активаций: *" + count + "*.", true);
    }

    private void adminSetTtl(Long chatId, BotUser admin, String text) {
        Integer hours = parseIntOrNull(text);
        if (hours == null || hours < 1) {
            safeSend(chatId, "❌ Ожидается целое число ≥ 1.");
            return;
        }
        settingsService.setActivationTtlHours(hours);
        logAction(admin, null, "SET_TTL", true, "hours=" + hours);
        safeSend(chatId, "✅ Время активации установлено: *" + hours + " ч*.\n"
                + "Новые и продлеваемые активации будут действовать этот срок.", true);
    }

    private void adminAddUser(Long chatId, BotUser admin, String text) {
        Long targetId = parseLongOrNull(text);
        if (targetId == null) {
            safeSend(chatId, "❌ Некорректный Telegram ID. Ожидается число.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        BotUser u = userRepository.findById(targetId).orElseGet(() -> BotUser.builder()
                .telegramId(targetId)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        u.setHasAccess(true);
        if (u.getFirstSeenAt() == null) u.setFirstSeenAt(now);
        if (u.getLastSeenAt() == null) u.setLastSeenAt(now);
        userRepository.save(u);
        logAction(admin, null, "ADD_USER", true, "target=" + targetId);
        safeSend(chatId, "✅ Пользователь добавлен с доступом: " + describeUser(u), true);
    }

    private void adminDeleteUser(Long chatId, BotUser admin, String text) {
        Long targetId = parseLongOrNull(text);
        if (targetId == null) {
            safeSend(chatId, "❌ Некорректный Telegram ID. Ожидается число.");
            return;
        }
        if (isAdmin(targetId)) {
            safeSend(chatId, "❌ Нельзя удалить администратора " + targetId + " (задан в BOT_ADMINS).");
            return;
        }
        if (!userRepository.existsById(targetId)) {
            safeSend(chatId, "❌ Пользователь " + targetId + " не найден.");
            return;
        }
        userRepository.deleteById(targetId);
        logAction(admin, null, "DELETE_USER", true, "target=" + targetId);
        safeSend(chatId, "🗑 Пользователь " + targetId + " удалён.");
    }

    private void adminDeleteVin(Long chatId, BotUser admin, String text) {
        String vin = text.trim().toUpperCase();
        Optional<AccessRecord> found = accessRepository.findByVin(vin);
        if (found.isEmpty()) {
            safeSend(chatId, "❌ VIN `" + vin + "` не найден.", true);
            return;
        }
        accessRepository.delete(found.get());
        logAction(admin, vin, "DELETE_VIN", true, "removed from whitelist");
        safeSend(chatId, "🗑 VIN `" + vin + "` удалён. authQuery для него теперь вернёт ошибку.", true);
    }

    private void sendUsersList(Long chatId, BotUser admin) {
        List<BotUser> users = userRepository.findAll();
        if (users.isEmpty()) {
            safeSend(chatId, "Пользователей пока нет.");
            return;
        }
        StringBuilder sb = new StringBuilder("👥 *Пользователи бота* (").append(users.size()).append(")\n\n");
        for (BotUser u : users) {
            String marker = isAdmin(u) ? "👑 " : (u.isHasAccess() ? "✅ " : "⛔️ ");
            sb.append(marker)
                    .append("`").append(u.getTelegramId()).append("`");
            if (u.getUsername() != null) sb.append(" @").append(escapeMd(u.getUsername()));
            if (u.getFirstName() != null) sb.append(" — ").append(escapeMd(u.getFirstName()));
            sb.append(" · активаций: *")
                    .append(isAdmin(u) ? "∞" : u.getActivationsRemaining())
                    .append("*");
            if (u.getLastSeenAt() != null) sb.append(" · ").append(u.getLastSeenAt().format(TS));
            sb.append('\n');
        }
        logAction(admin, null, "LIST_USERS", true, "count=" + users.size());
        safeSend(chatId, sb.toString(), true);
    }

    private void sendVinList(Long chatId, BotUser admin) {
        List<AccessRecord> records = accessRepository.findAll();
        if (records.isEmpty()) {
            safeSend(chatId, "Активированных VIN пока нет.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        var userMap = loadUsersByIds(records.stream().map(AccessRecord::getTelegramId).toList());

        long activeCount = records.stream()
                .filter(r -> r.getExpiresAt() != null && r.getExpiresAt().isAfter(now))
                .count();

        StringBuilder sb = new StringBuilder("📋 *VIN в базе*\n")
                .append("Всего: ").append(records.size())
                .append(" · Активных: ").append(activeCount)
                .append("\n━━━━━━━━━━━━━━━━━━\n");

        for (AccessRecord r : records) {
            boolean active = r.getExpiresAt() != null && r.getExpiresAt().isAfter(now);
            sb.append('\n').append(active ? "✅ " : "⌛️ ")
                    .append("`").append(r.getVin()).append("`\n");
            if (r.getFactoryCode() != null) {
                sb.append("   🔑 Код: `").append(r.getFactoryCode()).append("`\n");
            }
            sb.append("   👤 Владелец: ").append(displayUser(userMap.get(r.getTelegramId()), r.getTelegramId())).append('\n');
            if (r.getExpiresAt() != null) {
                sb.append("   ⏱ ").append(active ? "Действует до " : "Истёк ")
                        .append(r.getExpiresAt().format(TS)).append('\n');
            }
        }
        logAction(admin, null, "LIST_VINS", true, "count=" + records.size());
        safeSend(chatId, sb.toString(), true);
    }

    private void sendLogs(Long chatId, BotUser admin) {
        List<ActivationLog> logs = logRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20));
        if (logs.isEmpty()) {
            safeSend(chatId, "Журнал пуст.");
            return;
        }

        var userMap = loadUsersByIds(logs.stream().map(ActivationLog::getTelegramId).toList());
        long okCount = logs.stream().filter(ActivationLog::isSuccess).count();

        StringBuilder sb = new StringBuilder("📊 *Журнал действий*\n")
                .append("Показано: ").append(logs.size())
                .append(" · Успешных: ").append(okCount)
                .append(" · Ошибок: ").append(logs.size() - okCount)
                .append("\n━━━━━━━━━━━━━━━━━━\n");

        for (ActivationLog l : logs) {
            sb.append('\n')
                    .append(l.isSuccess() ? "✅ " : "❌ ")
                    .append("*").append(actionLabel(l.getAction())).append("*");
            if (l.getCreatedAt() != null) {
                sb.append("  ·  🕒 ").append(l.getCreatedAt().format(TS));
            }
            sb.append('\n');

            String who = displayUser(userMap.get(l.getTelegramId()), l.getTelegramId());
            if (l.getTelegramId() != null && l.getTelegramId() != 0L) {
                sb.append("   👤 ").append(who).append('\n');
            } else if (l.getTelegramId() != null) {
                sb.append("   🔐 Внешний запрос (API)\n");
            }

            if (l.getVin() != null && !l.getVin().isBlank()) {
                sb.append("   🚗 `").append(l.getVin()).append("`\n");
            }
            if (l.getMessage() != null && !l.getMessage().isBlank()) {
                sb.append("   💬 ").append(escapeMd(l.getMessage())).append('\n');
            }
        }
        safeSend(chatId, sb.toString(), true);
    }

    private String actionLabel(String action) {
        if (action == null) return "—";
        return switch (action) {
            case "BOT_OPEN" -> "Первый визит";
            case "GENERATE_REQUEST" -> "Попытка активации";
            case "ACTIVATE" -> "Активация VIN";
            case "AUTH_QUERY" -> "Проверка через API";
            case "ADD_USER" -> "Пользователь добавлен";
            case "DELETE_USER" -> "Пользователь удалён";
            case "DELETE_VIN" -> "VIN удалён";
            case "LIST_USERS" -> "Просмотр пользователей";
            case "LIST_VINS" -> "Просмотр VIN";
            case "SET_LIMIT" -> "Выдача активаций";
            case "SET_TTL" -> "Изменение времени активации";
            default -> action;
        };
    }

    private Map<Long, BotUser> loadUsersByIds(List<Long> ids) {
        List<Long> unique = ids.stream()
                .filter(id -> id != null && id != 0L)
                .distinct()
                .toList();
        if (unique.isEmpty()) return Map.of();
        Map<Long, BotUser> map = new HashMap<>();
        userRepository.findAllById(unique).forEach(u -> map.put(u.getTelegramId(), u));
        return map;
    }

    private String displayUser(BotUser u, Long fallbackId) {
        if (u == null) {
            return fallbackId == null || fallbackId == 0L ? "—" : "`" + fallbackId + "`";
        }
        StringBuilder sb = new StringBuilder();
        if (u.getUsername() != null && !u.getUsername().isBlank()) {
            sb.append("@").append(escapeMd(u.getUsername()));
        } else if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
            sb.append(escapeMd(u.getFirstName()));
        } else {
            sb.append("`").append(u.getTelegramId()).append("`");
            return sb.toString();
        }
        sb.append(" (`").append(u.getTelegramId()).append("`)");
        return sb.toString();
    }

    private void sendWelcome(Long chatId, BotUser user) {
        String access;
        if (isAdmin(user)) {
            access = "👑 Вы администратор. Активации не расходуются.\n"
                    + "⏱ Текущее время активации: *" + settingsService.getActivationTtlHours() + " ч*";
        } else if (user.getActivationsRemaining() > 0) {
            access = "✅ Доступно активаций: *" + user.getActivationsRemaining() + "*\n"
                    + "⏱ Каждая активация действует: *" + settingsService.getActivationTtlHours() + " ч*";
        } else {
            access = "⛔️ У вас нет активаций. Ваш ID: `" + user.getTelegramId() + "`";
        }
        String text = "Здравствуйте, " + safeName(user) + "!\n\n" + access + "\n\nВыберите действие:";
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.enableMarkdown(true);
        msg.setReplyMarkup(mainKeyboard(user));
        safeExecute(msg);
    }

    private void sendInstruction(Long chatId, BotUser user) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(INSTRUCTION_TEXT);
        msg.enableMarkdown(true);
        msg.setReplyMarkup(mainKeyboard(user));
        safeExecute(msg);
    }

    private void promptWithCancel(Long chatId, String text) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(BTN_CANCEL);
        rows.add(row);
        kb.setKeyboard(rows);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setReplyMarkup(kb);
        safeExecute(msg);
    }

    private ReplyKeyboardMarkup mainKeyboard(BotUser user) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setSelective(false);
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(BTN_GENERATE);
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(BTN_INSTRUCTION);
        rows.add(row2);

        if (isAdmin(user)) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(BTN_ADMIN_USERS);
            row3.add(BTN_ADMIN_LOGS);
            rows.add(row3);

            KeyboardRow row4 = new KeyboardRow();
            row4.add(BTN_ADMIN_SET_LIMIT);
            row4.add(BTN_ADMIN_SET_TTL);
            rows.add(row4);

            KeyboardRow row5 = new KeyboardRow();
            row5.add(BTN_ADMIN_ADD_USER);
            row5.add(BTN_ADMIN_DELETE_USER);
            rows.add(row5);

            KeyboardRow row6 = new KeyboardRow();
            row6.add(BTN_ADMIN_VINS);
            row6.add(BTN_ADMIN_DELETE_VIN);
            rows.add(row6);
        }

        kb.setKeyboard(rows);
        return kb;
    }

    private BotUser touchUser(User from) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.findById(from.getId())
                .map(existing -> {
                    existing.setUsername(from.getUserName());
                    existing.setFirstName(from.getFirstName());
                    existing.setLastName(from.getLastName());
                    existing.setLastSeenAt(now);
                    if (props.getAdminIds().contains(from.getId())) {
                        existing.setHasAccess(true);
                    }
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    BotUser created = BotUser.builder()
                            .telegramId(from.getId())
                            .username(from.getUserName())
                            .firstName(from.getFirstName())
                            .lastName(from.getLastName())
                            .hasAccess(props.getAdminIds().contains(from.getId()))
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .build();
                    BotUser saved = userRepository.save(created);
                    logRepository.save(ActivationLog.builder()
                            .telegramId(from.getId())
                            .username(from.getUserName())
                            .action("BOT_OPEN")
                            .success(true)
                            .message("first visit")
                            .createdAt(now)
                            .build());
                    return saved;
                });
    }

    private boolean hasAccess(BotUser user) {
        return user.isHasAccess() || isAdmin(user);
    }

    private boolean isAdmin(BotUser user) {
        return isAdmin(user.getTelegramId());
    }

    private boolean isAdmin(Long telegramId) {
        return telegramId != null && props.getAdminIds().contains(telegramId);
    }

    private void logAction(BotUser user, String vin, String action, boolean success, String msg) {
        logRepository.save(ActivationLog.builder()
                .telegramId(user.getTelegramId())
                .username(user.getUsername())
                .vin(vin)
                .action(action)
                .success(success)
                .message(msg)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String describeUser(BotUser u) {
        StringBuilder sb = new StringBuilder("`").append(u.getTelegramId()).append("`");
        if (u.getUsername() != null) sb.append(" @").append(escapeMd(u.getUsername()));
        if (u.getFirstName() != null) sb.append(" (").append(escapeMd(u.getFirstName())).append(")");
        return sb.toString();
    }

    private String safeName(BotUser user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return escapeMd(user.getFirstName());
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return "@" + escapeMd(user.getUsername());
        }
        return "друг";
    }

    private String escapeMd(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '*' || c == '`' || c == '[') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private Long parseLongOrNull(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void safeSend(Long chatId, String text) {
        safeSend(chatId, text, false);
    }

    private void safeSend(Long chatId, String text, boolean markdown) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        if (markdown) {
            msg.enableMarkdown(true);
        }
        safeExecute(msg);
    }

    private void safeExecute(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Telegram send error: {}", e.getMessage());
        }
    }
}
