package com.easycode.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_cents", nullable = false)
    private Integer unitCents;

    @Column(name = "position", nullable = false)
    private short position = 0;

    public int totalCents() {
        return quantity.multiply(BigDecimal.valueOf(unitCents)).intValue();
    }
}
