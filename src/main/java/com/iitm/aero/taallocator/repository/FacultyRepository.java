package com.iitm.aero.taallocator.repository;

import com.iitm.aero.taallocator.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Faculty findByNameIgnoreCase(String name);
    List<Faculty> findAllByNameIgnoreCase(String name);
}
