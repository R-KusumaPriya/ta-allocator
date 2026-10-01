package com.iitm.aero.taallocator.repository;

import com.iitm.aero.taallocator.model.PreferenceResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreferenceRepository extends JpaRepository<PreferenceResponse, Long> {
    List<PreferenceResponse> findBySelectCourse(String courseNo);
}
