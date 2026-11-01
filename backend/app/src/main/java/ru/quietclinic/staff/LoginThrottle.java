package ru.quietclinic.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ограничение попыток входа: после {@code max-per-login} неудач с одного адреса на один логин или
 * {@code max-per-address} неудач с одного адреса вообще вход с этого адреса закрыт до конца окна.
 * Счётчики в памяти процесса: перезапуск их сбрасывает. Пароли и логины в лог не пишутся.
 */
@Component
class LoginThrottle {
  private final int maxPerLogin;
  private final int maxPerAddress;
  private final Duration window;
  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

  LoginThrottle(
      @Value("${clinic.login.max-failures-per-login:5}") int maxPerLogin,
      @Value("${clinic.login.max-failures-per-address:20}") int maxPerAddress,
      @Value("${clinic.login.window:PT15M}") Duration window) {
    this.maxPerLogin = maxPerLogin;
    this.maxPerAddress = maxPerAddress;
    this.window = window;
  }

  /** true — с этого адреса на этот логин сейчас входить нельзя. */
  boolean blocked(String address, String username, Instant now) {
    return count(loginKey(address, username), now) >= maxPerLogin
        || count(addressKey(address), now) >= maxPerAddress;
  }

  void failed(String address, String username, Instant now) {
    add(loginKey(address, username), now);
    add(addressKey(address), now);
  }

  /** Удачный вход сбрасывает счётчик этого логина с этого адреса. */
  void succeeded(String address, String username) {
    failures.remove(loginKey(address, username));
  }

  private int count(String key, Instant now) {
    Deque<Instant> list = failures.get(key);
    if (list == null) return 0;
    synchronized (list) {
      while (!list.isEmpty() && list.peekFirst().isBefore(now.minus(window))) list.pollFirst();
      return list.size();
    }
  }

  private void add(String key, Instant now) {
    Deque<Instant> list = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (list) {
      list.addLast(now);
      while (list.size() > Math.max(maxPerLogin, maxPerAddress)) list.pollFirst();
    }
  }

  private static String loginKey(String address, String username) {
    return "login:" + address + ":" + username.trim().toLowerCase(Locale.ROOT);
  }

  private static String addressKey(String address) {
    return "address:" + address;
  }
}
