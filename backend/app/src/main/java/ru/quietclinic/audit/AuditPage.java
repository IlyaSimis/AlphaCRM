package ru.quietclinic.audit;

import java.util.List;

/** Страница журнала: события, номер страницы с нуля, размер и сколько событий всего. */
record AuditPage(List<AuditView> items, int page, int size, long total) {
  AuditPage withItems(List<AuditView> named) {
    return new AuditPage(named, page, size, total);
  }
}
