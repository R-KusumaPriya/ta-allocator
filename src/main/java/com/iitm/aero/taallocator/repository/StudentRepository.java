package com.iitm.aero.taallocator.repository;

import com.iitm.aero.taallocator.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {
    List<Student> findByIsAvailableTrue();
}
