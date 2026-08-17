package com.easycode.api.repo;

import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<UserAccount> findByOrgId(UUID orgId);

    List<UserAccount> findByRoleIn(List<Role> roles);
}
