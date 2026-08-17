package com.easycode.api.repo;

import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.enums.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<Invoice> findByStatusOrderByDueAtAsc(InvoiceStatus status);

    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);

    Optional<Invoice> findByNumber(String number);

    long countByStatus(InvoiceStatus status);

    @Query("select coalesce(sum(i.amountCents - i.amountPaidCents), 0) from Invoice i "
            + "where i.orgId = :orgId and i.status = com.easycode.api.domain.enums.InvoiceStatus.OPEN")
    long outstandingCentsForOrg(UUID orgId);

    @Query("select coalesce(sum(i.amountCents - i.amountPaidCents), 0) from Invoice i "
            + "where i.status = com.easycode.api.domain.enums.InvoiceStatus.OPEN")
    long outstandingCentsTotal();

    @Query("select coalesce(max(i.number), '') from Invoice i where i.number like concat(:prefix, '%')")
    String maxNumberWithPrefix(String prefix);
}
