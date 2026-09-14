package com.orgasm.backend.spotify;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional("appTransactionManager")
public interface SpotifyConnectionRepository extends JpaRepository<SpotifyConnection, Long> {

    Optional<SpotifyConnection> findByContributor_Id(Long contributorId);
}
