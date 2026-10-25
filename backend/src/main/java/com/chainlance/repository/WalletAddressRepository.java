package com.chainlance.repository;

import com.chainlance.entity.WalletAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletAddressRepository extends JpaRepository<WalletAddress, Long> {

    /** Addresses are stored lowercase; callers must lowercase before searching. */
    Optional<WalletAddress> findByAddress(String address);

    boolean existsByAddress(String address);

    boolean existsByAddressAndUserId(String address, Long userId);

    Optional<WalletAddress> findByAddressAndUserId(String address, Long userId);

    List<WalletAddress> findByUserIdOrderByCreatedAtAsc(Long userId);
}
