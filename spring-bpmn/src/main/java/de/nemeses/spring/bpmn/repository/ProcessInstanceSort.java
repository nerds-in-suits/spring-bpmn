package de.nemeses.spring.bpmn.repository;

/**
 * Sort order of {@link ProcessInstanceRepository#query}.
 */
public enum ProcessInstanceSort {

    /** By {@link ProcessInstance#startDate()}. */
    START_DATE,

    /** By {@link ProcessInstance#endDate()}; running instances (no end date) count as later than all ended ones. */
    END_DATE
}
