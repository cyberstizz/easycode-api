package com.easycode.api.repo;

import com.easycode.api.domain.LeadActivity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    List<LeadActivity> findByLeadIdOrderByOccurredAtDesc(UUID leadId);

    long countByLeadId(UUID leadId);

    long countByLeadIdAndOutcome(UUID leadId, com.easycode.api.domain.enums.ActivityOutcome outcome);

    long countByTypeAndOccurredAtAfter(com.easycode.api.domain.enums.ActivityType type, Instant since);

    /**
     * "Where deals die" — objection tags counted across leads that ended LOST.
     *
     * <p>Native because it unnests a Postgres text[], which JPQL can't express.
     * The GIN index added in V2 keeps this cheap.
     */
    @Query(value = """
            select tag, count(*) as losses
            from lead_activities a
            join leads l on l.id = a.lead_id
            cross join lateral unnest(a.objection_tags) as tag
            where l.status = 'LOST'
            group by tag
            order by losses desc
            limit 6
            """, nativeQuery = true)
    List<Object[]> objectionCountsForLostLeads();

    /**
     * "Which offer closes" — for each rung ever put on the table, how often the
     * lead ended WON versus how many times that rung was offered at all.
     */
    @Query(value = """
            select a.rung_offered,
                   count(*) filter (where l.status = 'WON') as wins,
                   count(*)                                 as offered
            from lead_activities a
            join leads l on l.id = a.lead_id
            where a.rung_offered is not null
            group by a.rung_offered
            order by offered desc
            """, nativeQuery = true)
    List<Object[]> rungConversion();

    /** Dials since a point in time, for the daily and weekly counters. */
    @Query(value = """
            select count(*) from lead_activities
            where type = 'CALL' and occurred_at >= :since
              and (:userId is null or user_id = :userId)
            """, nativeQuery = true)
    long dialsSince(@Param("since") Instant since, @Param("userId") UUID userId);
}