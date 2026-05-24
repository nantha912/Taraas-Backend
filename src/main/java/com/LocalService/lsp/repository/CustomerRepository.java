package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {

    /**
     * Finds a customer by their unique email address.
     * Essential for the login/authentication flow in CustomerService.
     */
    Optional<Customer> findByEmail(String email);

    /**
     * Checks if a customer already exists with the given email.
     * Used in the registration logic to prevent duplicate accounts.
     */
    Boolean existsByEmail(String email);

}