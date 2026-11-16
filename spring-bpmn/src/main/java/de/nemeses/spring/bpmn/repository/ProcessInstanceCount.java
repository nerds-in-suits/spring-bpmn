package de.nemeses.spring.bpmn.repository;

/**
 * Number of running and finished instances of a process definition (all its versions), see
 * {@link ProcessInstanceRepository#countByDefinition()}.
 *
 * @param running the number of instances without end date
 * @param finished the number of instances with end date
 */
public record ProcessInstanceCount(long running, long finished) {
}
