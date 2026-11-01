package ru.quietclinic.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Отправка операций маркировки оператору через {@link MarkingPort} с повторами. Успех двигает
 * статусы марок (приёмка → IN_STOCK, выбытие → WITHDRAWN …); отказ после {@link #MAX_ATTEMPTS} —
 * ERROR у операции и марок. «Не настроено» не считается попыткой: операция ждёт подключения.
 */
@Component
class MarkingRelay {
  static final int MAX_ATTEMPTS = 5;
  static final Duration NOT_CONFIGURED_PAUSE = Duration.ofHours(1);
  private static final Logger log = LoggerFactory.getLogger(MarkingRelay.class);

  private final MarkingOperationRepository operations;
  private final MarkingCodeRepository codes;
  private final MarkingPort port;
  private final TransactionTemplate transaction;

  MarkingRelay(
      MarkingOperationRepository operations,
      MarkingCodeRepository codes,
      MarkingPort port,
      PlatformTransactionManager transactions) {
    this.operations = operations;
    this.codes = codes;
    this.port = port;
    this.transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  String adapter() {
    return port.adapter();
  }

  /** Все операции, чей срок пришёл. */
  int runOnce(Instant now) {
    List<MarkingOperation> due =
        operations.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            MarkingOperation.PENDING, now);
    due.forEach(op -> process(op.id));
    return due.size();
  }

  void process(UUID operationId) {
    try {
      transaction.executeWithoutResult(status -> send(operationId));
    } catch (RuntimeException ex) {
      log.warn("Marking operation {} failed: {}", operationId, ex.getClass().getSimpleName());
    }
  }

  private void send(UUID operationId) {
    MarkingOperation op = operations.findById(operationId).orElse(null);
    if (op == null || !MarkingOperation.PENDING.equals(op.status)) return;
    MarkingPort.Result result =
        switch (op.type) {
          case MarkingOperation.ACCEPTANCE -> port.accept(op.id, op.sgtins);
          case MarkingOperation.WITHDRAW_MEDICAL_USE ->
              port.withdrawForMedicalUse(op.id, op.sgtins);
          case MarkingOperation.WRITE_OFF -> port.writeOff(op.id, op.sgtins);
          case MarkingOperation.RETURN_TO_SUPPLIER -> port.returnToSupplier(op.id, op.sgtins);
          default -> MarkingPort.Result.rejected("UNKNOWN_OPERATION");
        };
    Instant now = Instant.now();
    op.updatedAt = now;
    if (result.accepted()) {
      op.status = MarkingOperation.DONE;
      op.operatorRef = result.operatorRef();
      op.lastError = null;
      op.attempts++;
      moveCodes(op, doneStatus(op.type));
    } else if (UnconfiguredMarkingAdapter.NOT_CONFIGURED.equals(result.errorCode())) {
      op.lastError = result.errorCode();
      op.nextAttemptAt = now.plus(NOT_CONFIGURED_PAUSE);
    } else {
      op.attempts++;
      op.lastError = result.errorCode();
      if (!result.retry() || op.attempts >= MAX_ATTEMPTS) {
        op.status = MarkingOperation.ERROR;
        moveCodes(op, "ERROR");
      } else {
        op.nextAttemptAt = now.plus(Duration.ofMinutes(5L << Math.min(op.attempts, 6)));
      }
    }
  }

  private void moveCodes(MarkingOperation op, String status) {
    for (MarkingCode code : codes.findByClinicIdAndSgtinIn(op.clinicId, op.sgtins)) {
      code.status = status;
      code.updatedAt = Instant.now();
      if (!"IN_STOCK".equals(status) && !"ERROR".equals(status)) code.warehouseId = null;
    }
  }

  static String doneStatus(String type) {
    return switch (type) {
      case MarkingOperation.ACCEPTANCE -> "IN_STOCK";
      case MarkingOperation.WITHDRAW_MEDICAL_USE -> "WITHDRAWN";
      case MarkingOperation.WRITE_OFF -> "WRITTEN_OFF";
      case MarkingOperation.RETURN_TO_SUPPLIER -> "RETURNED";
      default -> "ERROR";
    };
  }
}
