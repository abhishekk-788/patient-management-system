package com.pm.patientservice.repository;

import com.pm.patientservice.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID id);
}

/*
 `@Repository` tells Spring:
   - "Please create an object (Bean) of this interface."
   - Spring will manage it automatically.
   - You NEVER create repository objects manually.
     Example: you never write new PatientRepository().
 Why?
   - Spring Boot uses Dependency Injection.
   - This means: Spring creates objects for you and provides them wherever needed.
   - So this Repository becomes a Bean managed by Spring.

 A Repository is the DATA ACCESS layer.
   - It talks to the database.
   - It saves, fetches, updates, deletes data.
   - You do NOT write SQL manually.
   - Spring Data JPA creates SQL queries based on method names.
*/