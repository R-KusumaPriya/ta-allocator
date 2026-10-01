package com.iitm.aero.taallocator.repository;

import com.iitm.aero.taallocator.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, String> {
    Faculty findByNameIgnoreCase(String name);
}
