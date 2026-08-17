package com.easycode.api.repo;

import com.easycode.api.domain.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {}
