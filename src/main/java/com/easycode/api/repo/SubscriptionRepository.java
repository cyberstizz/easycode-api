package com.easycode.api.repo;

import com.easycode.api.domain.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByOrgId(UUID orgId);

    Optional<Subscription> findByStripeSubId(String stripeSubId);
}
