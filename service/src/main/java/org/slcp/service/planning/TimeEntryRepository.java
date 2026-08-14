package org.slcp.service.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

	List<TimeEntry> findByActivityIdOrderByWorkedOnAsc(UUID activityId);

	@Query(value = "SELECT COALESCE(SUM(hours), 0) FROM time_entries WHERE activity_id = :actividad",
			nativeQuery = true)
	BigDecimal horasDe(@Param("actividad") UUID activityId);
}
