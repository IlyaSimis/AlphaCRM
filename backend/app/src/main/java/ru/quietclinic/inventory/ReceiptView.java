package ru.quietclinic.inventory;

import java.util.List;

/**
 * Итог быстрого прихода: накладная, проведена ли она, сколько позиций заведено. waitingCodes —
 * маркируемые позиции без отсканированных марок: тогда накладная остаётся черновиком.
 */
record ReceiptView(
    DocumentView document, boolean posted, int itemsCreated, List<String> waitingCodes) {}
