package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.ApplicationEligibilityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationEligibilityAnswerRepository extends JpaRepository<ApplicationEligibilityAnswer, Long> {

    /** Fetch all answers for a specific application, ordered by display order for presentation. */
    List<ApplicationEligibilityAnswer> findByApplicationIdOrderByDisplayOrderAscIdAsc(Long applicationId);

    /** Delete all answers for an application (used when re-applying). */
    void deleteByApplicationId(Long applicationId);
}
