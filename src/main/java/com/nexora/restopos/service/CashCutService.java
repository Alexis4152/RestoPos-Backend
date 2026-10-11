package com.nexora.restopos.service;

import com.nexora.restopos.dto.CashCutRequest;
import com.nexora.restopos.dto.CashCutSummary;
import com.nexora.restopos.model.CashCutStatus;
import com.nexora.restopos.model.CashMovementType;
import com.nexora.restopos.model.PaymentMethod;
import com.nexora.restopos.model.CashCut;
import com.nexora.restopos.model.MovimientoCaja;
import com.nexora.restopos.model.Pago;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.CashCutRepository;
import com.nexora.restopos.repository.MovimientoCajaRepository;
import com.nexora.restopos.repository.PagoRepository;
import com.nexora.restopos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Administra el ciclo de vida de los cortes de caja ({@link CashCut}): apertura, consulta,
 * resumen y cierre (manual o automático).
 *
 * <p>Cada usuario con acceso a la sección {@code CASH_CUTS} abre y cierra su propio corte, con
 * su propio fondo inicial; puede haber varios cortes abiertos a la vez dentro de la misma
 * tienda. Los totales del corte se calculan a partir de los {@link Pago} atados a él (no de una
 * tabla de ventas separada) y de los {@link MovimientoCaja} manuales (entradas/salidas/ajustes)
 * registrados durante el turno. El correo con el reporte de cierre NO se envía desde aquí: lo
 * dispara el job una sola vez al día por tienda (ver {@code CashCutReportNotifier}).</p>
 */
@Service
@RequiredArgsConstructor
public class CashCutService {

    private static final String ADMIN = "ADMIN";

    private final CashCutRepository cashCutRepository;
    private final PagoRepository pagoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final TenantScope tenantScope;

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    public Optional<CashCut> findOpen(User actor) {
        return cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN);
    }

    public Page<CashCut> findAll(LocalDateTime from, LocalDateTime to, CashCutStatus status, Pageable pageable, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return cashCutRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, status, pageable);
    }

    public Optional<CashCut> findMineToday(User actor) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return cashCutRepository.findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                actor.getId(), startOfDay, endOfDay);
    }

    public CashCut findById(Long id, User actor) {
        CashCut cut = findById(id);
        if (!canView(cut, actor)) {
            throw new IllegalArgumentException("Corte no encontrado: " + id);
        }
        return cut;
    }

    private CashCut findById(Long id) {
        return cashCutRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado: " + id));
    }

    // ADMIN ve cualquier corte de su tienda; SUPER_ADMIN ve todo; SUPERVISOR ve los de la
    // tienda sobre la que eligió actuar (si de verdad está a su cargo); el resto solo el
    // suyo del día.
    private boolean canView(CashCut cut, User actor) {
        if (tenantScope.isSuperAdmin(actor)) return true;
        if (tenantScope.isSupervisor(actor)) {
            Long scope = tenantScope.scopeId(actor); // -1 si no ha elegido tienda
            return cut.getTienda() != null && cut.getTienda().getId().equals(scope);
        }
        if (ADMIN.equals(actor.getRole().getName())) {
            return actor.getTienda() == null ? cut.getTienda() == null
                    : actor.getTienda().getId().equals(cut.getTienda() != null ? cut.getTienda().getId() : null);
        }
        return cut.getUser().getId().equals(actor.getId())
                && cut.getOpenedAt().toLocalDate().equals(LocalDate.now());
    }

    @Transactional
    public CashCut open(CashCutRequest req, User actor) {
        if (!ADMIN.equals(actor.getRole().getName())) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            if (cashCutRepository.existsByUserIdAndOpenedAtBetween(actor.getId(), startOfDay, endOfDay)) {
                throw new IllegalStateException(
                        "Ya abriste un corte de caja hoy. Solo un administrador puede abrir varios cortes el mismo día.");
            }
        }
        CashCut cut = new CashCut();
        cut.setUser(actor);
        cut.setTienda(actor.getTienda());
        cut.setOpeningAmount(req.getAmount());
        cut.setStatus(CashCutStatus.OPEN);
        cut.setNotes(req.getNotes());
        return cashCutRepository.save(cut);
    }

    public CashCutSummary summary(Long id, User actor) {
        CashCut cut = findById(id, actor);
        List<Pago> pagos = pagoRepository.findByCashCutId(id);
        List<MovimientoCaja> movimientos = movimientoCajaRepository.findByCashCutIdOrderByCreatedAtAsc(id);
        Totales totales = sumarPagos(pagos);
        BigDecimal[] entradasSalidas = sumarMovimientos(movimientos);
        return new CashCutSummary(cut.getOpeningAmount(), totales.cashSales, totales.cardSales, totales.transferSales,
                totales.totalSales, totales.transactionCount, totales.cancelledCount, totales.cancelledTotal,
                entradasSalidas[0], entradasSalidas[1]);
    }

    @Transactional
    public CashCut close(Long id, CashCutRequest req, User actor) {
        CashCut cut = findById(id, actor);
        ensureOpen(cut);
        BigDecimal expenses = req.getExpenses() != null ? req.getExpenses() : BigDecimal.ZERO;
        return closeInternal(cut, expenses, req.getNotes(), actor);
    }

    @Transactional
    public CashCut autoClose(Long id) {
        CashCut cut = findById(id);
        ensureOpen(cut);
        return closeInternal(cut, BigDecimal.ZERO, "Cerrado automáticamente por el sistema (corte del día).", null);
    }

    private void ensureOpen(CashCut cut) {
        if (cut.getStatus() != CashCutStatus.OPEN) {
            throw new IllegalStateException("El corte ya está cerrado");
        }
    }

    // fondo final = fondo inicial + ventas en efectivo - gastos + entradas manuales - salidas manuales
    // (las ventas con tarjeta/transferencia no mueven el efectivo físico en caja)
    private CashCut closeInternal(CashCut cut, BigDecimal expenses, String notes, User closedBy) {
        List<Pago> pagos = pagoRepository.findByCashCutId(cut.getId());
        List<MovimientoCaja> movimientos = movimientoCajaRepository.findByCashCutIdOrderByCreatedAtAsc(cut.getId());
        Totales totales = sumarPagos(pagos);
        BigDecimal[] entradasSalidas = sumarMovimientos(movimientos);

        BigDecimal closingAmount = cut.getOpeningAmount()
                .add(totales.cashSales)
                .subtract(expenses)
                .add(entradasSalidas[0])
                .subtract(entradasSalidas[1]);

        cut.setExpenses(expenses);
        cut.setClosingAmount(closingAmount);
        cut.setTotalSales(totales.totalSales);
        cut.setCashSales(totales.cashSales);
        cut.setCardSales(totales.cardSales);
        cut.setTransferSales(totales.transferSales);
        cut.setTotalTransactions(totales.transactionCount);
        cut.setCancelledCount(totales.cancelledCount);
        cut.setCancelledTotal(totales.cancelledTotal);
        cut.setTotalEntradas(entradasSalidas[0]);
        cut.setTotalSalidas(entradasSalidas[1]);
        cut.setStatus(CashCutStatus.CLOSED);
        cut.setClosedAt(LocalDateTime.now());
        cut.setClosedBy(closedBy);
        if (notes != null) cut.setNotes(notes);
        return cashCutRepository.save(cut);
    }

    private Totales sumarPagos(List<Pago> pagos) {
        Totales totales = new Totales();
        for (Pago p : pagos) {
            if (Boolean.TRUE.equals(p.getCancelado())) {
                totales.cancelledCount++;
                totales.cancelledTotal = totales.cancelledTotal.add(p.getMonto());
                continue;
            }
            totales.transactionCount++;
            totales.totalSales = totales.totalSales.add(p.getMonto());
            if (p.getMetodo() == PaymentMethod.CASH) totales.cashSales = totales.cashSales.add(p.getMonto());
            else if (p.getMetodo() == PaymentMethod.CARD) totales.cardSales = totales.cardSales.add(p.getMonto());
            else if (p.getMetodo() == PaymentMethod.TRANSFER) totales.transferSales = totales.transferSales.add(p.getMonto());
        }
        return totales;
    }

    // [0] = total entradas, [1] = total salidas; un AJUSTE cuenta como entrada si es positivo, salida si es negativo
    private BigDecimal[] sumarMovimientos(List<MovimientoCaja> movimientos) {
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal salidas = BigDecimal.ZERO;
        for (MovimientoCaja mv : movimientos) {
            if (mv.getTipo() == CashMovementType.ENTRADA) entradas = entradas.add(mv.getMonto());
            else if (mv.getTipo() == CashMovementType.SALIDA) salidas = salidas.add(mv.getMonto());
            else if (mv.getTipo() == CashMovementType.AJUSTE) {
                if (mv.getMonto().compareTo(BigDecimal.ZERO) >= 0) entradas = entradas.add(mv.getMonto());
                else salidas = salidas.add(mv.getMonto().abs());
            }
        }
        return new BigDecimal[]{entradas, salidas};
    }

    private static class Totales {
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal transferSales = BigDecimal.ZERO;
        int transactionCount = 0;
        int cancelledCount = 0;
        BigDecimal cancelledTotal = BigDecimal.ZERO;
    }
}
