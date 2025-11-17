package com.pm.patientservice.dto.validators;

public interface CreatePatientValidatorGroup { }

/*
 * This is a marker interface used for validation groups.
 * It helps apply specific validation rules only during
 * patient creation (not during update).
 *
 * Example usage:
 * @NotNull(groups = CreatePatientValidatorGroup.class)
 *
 * Spring uses this "group" to know when certain
 * validation constraints should run.
 */
