package com.hunor.classicmodelsbackend.repository;

import com.hunor.classicmodelsbackend.dto.employee.EmployeeDependentDTO;
import com.hunor.classicmodelsbackend.model.Employee;

import java.util.List;
import java.util.Optional;

/**
 * The repository contract for the {@code employees} aggregate.
 *
 * <p>Promoted from a concrete class to an interface specifically so that
 * the hand-rolled {@link com.hunor.classicmodelsbackend.tx.MyTransactional}
 * machinery can wrap implementations in JDK dynamic proxies (which require
 * an interface). The concrete JDBC implementation lives in
 * {@link JdbcEmployeeRepository}.</p>
 *
 * <h3>Why "program to an interface" earns its keep</h3>
 * <ul>
 *   <li>Tests can swap a mock implementation with one line in a test
 *       configuration, no proxy weaving or PowerMock needed.</li>
 *   <li>Adding a JPA, R2DBC, or in-memory implementation later doesn't
 *       require touching {@code EmployeeService}.</li>
 *   <li>Spring's AOP infrastructure (and ours) is happier with
 *       interfaces than with concrete classes.</li>
 * </ul>
 *
 * <p>The {@code @MyTransactional} annotation lives on the implementation
 * class. The proxy looks at the impl's methods, finds the annotation, and
 * routes those calls through the transaction interceptor.</p>
 */
public interface EmployeeRepository {

    Employee save(Employee e);

    Optional<Employee> findById(int id);

    List<Employee> findAll();

    /**
     * Find every active employee assigned to a particular office.
     * Used by the office detail page to render the team list.
     */
    List<Employee> findByOfficeCode(String officeCode);

    void update(Employee e);

    /** Plain hard delete. Throws on FK violation; usually you want one of the strategies below. */
    void delete(int id);

    /** Strategy 1: mark active=0, terminatedDate=today. Reversible. */
    void softDelete(int id);

    /** Strategy 2: NULL referencing FKs in customers and employees, then delete the row. */
    void deleteAndNullify(int id);

    /** Strategy 3: delete customers (no orders/payments allowed), null reportsTo, delete employee. */
    void deleteAndCascade(int id);

    /** Strategy 4: deep cascade through orders, orderdetails, payments, customers, then delete. */
    void deleteAndDeepCascade(int id);

    /**
     * Strategy 5: reassign customers + direct reports to {@code targetId},
     * then delete {@code sourceId}. All three statements run in one
     * transaction. The natural fit for "this employee is leaving — give
     * their customers to someone else."
     */
    void reassignAndDelete(int sourceId, int targetId);

    List<EmployeeDependentDTO> findDependents(int id);

    /**
     * Page over active employees, optionally filtered by a search term
     * that does a case-insensitive LIKE against name and email.
     *
     * @param search  null/blank for no filter; otherwise treated as
     *                a substring to match against lastName, firstName,
     *                or email.
     */
    List<Employee> findAllPaged(int page, int size, String sortBy, boolean asc, String search);

    /**
     * Total active rows matching {@code search} (or all active rows if
     * search is null/blank). Used by the frontend paginator to show
     * "showing 1–10 of 87".
     */
    long countAll(String search);

    void saveAll(List<Employee> employees);
}
