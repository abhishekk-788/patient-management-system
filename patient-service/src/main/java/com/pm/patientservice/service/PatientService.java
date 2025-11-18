package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
/*
 * @Service tells Spring Boot:
 *   - Create an object (Bean) of this class.
 *   - Manage its lifecycle.
 *   - Inject required dependencies into its constructor.
 *
 * This means you NEVER manually write:
 *     new PatientService(...)
 *
 * Instead, Spring automatically creates:
 *     PatientService serviceBean = new PatientService(repositoryBean)
 *
 * This automatic object creation is called DEPENDENCY INJECTION,
 * managed by Spring's IoC (Inversion of Control) Container.
 */
public class PatientService {
    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceClient;
    private final KafkaProducer kafkaProducer;

    /*
     * CONSTRUCTOR INJECTION:
     * Spring Boot automatically calls this constructor and passes
     * the PatientRepository Bean into it.
     *
     * You do NOT need to create PatientRepository manually.
     * Spring creates it because the interface is marked with @Repository.
     *
     * This makes:
     *    - Testing easier
     *    - Code cleaner
     *    - Dependencies visible
     */
    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceClient, KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceClient = billingServiceClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<PatientResponseDTO> getAllPatients() {
        List<Patient> patients = patientRepository.findAll();
        List<PatientResponseDTO> patientResponseDTOS = new ArrayList<>();
        for (Patient patient : patients) {
            PatientResponseDTO patientResponseDTO = PatientMapper.toPatientResponseDTO(patient);
            patientResponseDTOS.add(patientResponseDTO);
        }
        return patientResponseDTOS;
    }

    /*
     * CREATE PATIENT:
     * - Checks if email already exists (to avoid duplicates)
     * - Converts DTO -> Entity
     * - Saves entity into DB
     * - Converts Entity -> ResponseDTO
     */
    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {
        if(patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            throw new EmailAlreadyExistException("Email already exists");
        }
        Patient newPatient = patientRepository.save(PatientMapper.toPatient(patientRequestDTO));
        billingServiceClient.createBillingAccount(newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());
        kafkaProducer.sendEvent(newPatient);

        return PatientMapper.toPatientResponseDTO(newPatient);
    }

    @Transactional
    /*
     * @Transactional means:
     *   - All DB operations in this method will run in ONE transaction.
     *   - If any exception happens, everything is rolled back.
     *   - Ensures data consistency during updates.
     */
    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with id: " + id));

        String newEmail = patientRequestDTO.getEmail() == null ? null : patientRequestDTO.getEmail().trim().toLowerCase();

        if (newEmail != null && !newEmail.equalsIgnoreCase(patient.getEmail())) {
            if (patientRepository.existsByEmailAndIdNot(newEmail, id)) {
                throw new EmailAlreadyExistException("Email already exists");
            }
            patient.setEmail(newEmail);
        }

        if (patientRequestDTO.getName() != null) {
            patient.setName(patientRequestDTO.getName().trim());
        }

        if (patientRequestDTO.getAddress() != null) {
            patient.setAddress(patientRequestDTO.getAddress().trim());
        }

        if (patientRequestDTO.getDateOfBirth() != null) {
            try {
                LocalDate dob = LocalDate.parse(patientRequestDTO.getDateOfBirth());
                patient.setDateOfBirth(dob);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "dateOfBirth must be in yyyy-MM-dd format"
                );
            }
        }

        Patient updatedPatient = patientRepository.save(patient);
        return PatientMapper.toPatientResponseDTO(updatedPatient);
    }

    public void deletePatient(UUID id) {
        patientRepository.deleteById(id);
    }
}
