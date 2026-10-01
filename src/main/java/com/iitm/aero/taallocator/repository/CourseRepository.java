package com.iitm.aero.taallocator.repository;

import com.iitm.aero.taallocator.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, String> {
    List<Course> findByGroupId(double groupId);
}
