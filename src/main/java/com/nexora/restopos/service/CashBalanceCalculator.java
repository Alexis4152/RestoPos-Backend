package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.PaymentMethod;
import com.nexora.restopos.repository.CashMovementRepository;
import com.nexora.restopos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Calcula los totales de un corte a partir de sus ventas y sus movimientos de efectivo, y
 * de ahí el efectivo esperado en caja. Compartido por {@link CashCutService} (resumen y
 * cierre), {@link CashMovementService} (tope de las salidas) y {@link CashRegisterService}
 * (efectivo esperado de las cajas en uso), para que los tres usen exactamente la misma fórmula:
 *
 * <pre>esperado = fondo inicial + ventas en efectivo + entradas − gastos − retiros − reembolsos − préstamos a otras cajas</pre>
 *
 * Las ventas con tarjeta/transferencia no son dinero físico: se reportan pero no suman.
 */
@Component
@RequiredArgsConstructor
public class CashBalanceCalculator {

    private final PagoRepository pagoRepository;
    private final CashMovementRepository movementRepository;

    /** Resumen completo (ventas por método, cancelaciones, movimientos y efectivo esperado). */
    public CashCutSummary summarize(CashCut cut) {
        BigDecimal cash = BigDecimal.ZERO, card = BigDecimal.ZERO, transfer = BigDecimal.ZERO, total = BigDecimal.ZERO;
        BigDecimal cancelledTotal = BigDecimal.ZERO;
        int transactions = 0, cancelledCount = 0;

        // Consultas agregadas (no se carga ninguna venta ni movimiento): esto corre en casi
        // todo el flujo de cortes y no debe crecer con el número de ventas del turno.
        // En RestoPOS las "ventas" del corte son los cobros (Pago) registrados en él.
        for (Object[] row : pagoRepository.sumByCashCut(cut.getId())) {
            PaymentMethod method = (PaymentMethod) row[0];
            boolean cancelado = Boolean.TRUE.equals(row[1]);
            int count = ((Number) row[2]).intValue();
            BigDecimal sum = (BigDecimal) row[3];
            if (cancelado) {
                cancelledCount += count;
                cancelledTotal = cancelledTotal.add(sum);
                continue;
            }
            transactions += count;
            total = total.add(sum);
            if (method == PaymentMethod.CASH) cash = cash.add(sum);
            else if (method == PaymentMethod.CARD) card = card.add(sum);
            else if (method == PaymentMethod.TRANSFER) transfer = transfer.add(sum);
        }

        BigDecimal entries = BigDecimal.ZERO, expenses = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO, reimbursements = BigDecimal.ZERO;
        BigDecimal loansOut = BigDecimal.ZERO;
        for (Object[] row : movementRepository.sumByTypeForCut(cut.getId())) {
            BigDecimal sum = (BigDecimal) row[1];
            switch ((CashMovementType) row[0]) {
                case CONTRIBUTION, FLOAT, SHORTAGE_PAYMENT, LOAN_IN -> entries = entries.add(sum);
                case LOAN_OUT -> loansOut = loansOut.add(sum);
                case EXPENSE -> expenses = expenses.add(sum);
                case WITHDRAWAL -> withdrawals = withdrawals.add(sum);
                case REIMBURSEMENT -> reimbursements = reimbursements.add(sum);
            }
        }

        BigDecimal opening = cut.getOpeningAmount() != null ? cut.getOpeningAmount() : BigDecimal.ZERO;
        BigDecimal expected = opening.add(cash).add(entries)
                .subtract(expenses).subtract(withdrawals).subtract(reimbursements).subtract(loansOut);

        return CashCutSummary.builder()
                .openingAmount(opening)
                .cashSales(cash).cardSales(card).transferSales(transfer).totalSales(total)
                .totalTransactions(transactions)
                .cancelledCount(cancelledCount).cancelledTotal(cancelledTotal)
                .entriesTotal(entries).expensesTotal(expenses).withdrawalsTotal(withdrawals).reimbursementsTotal(reimbursements)
                .loansOutTotal(loansOut)
                .expectedCash(expected)
                .build();
    }

    /** Solo el efectivo esperado en caja ahora mismo. */
    public BigDecimal expectedCash(CashCut cut) {
        return summarize(cut).getExpectedCash();
    }
}
