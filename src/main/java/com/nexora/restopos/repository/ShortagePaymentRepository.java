package com.nexora.restopos.repository;

import com.nexora.restopos.model.ShortagePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShortagePaymentRepository extends JpaRepository<ShortagePayment, Long> {

    List<ShortagePayment> findByShortage_IdOrderByCreatedAtAsc(Long shortageId);
}
