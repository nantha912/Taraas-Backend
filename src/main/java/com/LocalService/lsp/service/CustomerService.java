package com.LocalService.lsp.service;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.security.auth.login.CredentialNotFoundException;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CustomerService(
            CustomerRepository customerRepository,
            TransactionRepository transactionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Finds a customer by their unique ID.
     */
    public Optional<Customer> getCustomerById(String id) {
        if (id == null) return Optional.empty();
        //logger.info("Service: Fetching customer by ID: {}", id);

        Optional<Customer> customerOpt = customerRepository.findById(id);
        customerOpt.ifPresent(this::attachBuyerCategory);

        return customerOpt;
    }

    /**
     * Checks if an email is already registered in the system.
     */
    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return customerRepository.existsByEmail(email);
    }

    /**
     * Registers a new customer.
     */
    public Customer registerCustomer(Customer customer) {
        if (customer == null || customer.getEmail() == null) {
            throw new IllegalArgumentException("Customer and email are required");
        }
        //logger.info("Attempting to register customer with email: {}", customer.getEmail());

        if (customerRepository.existsByEmail(customer.getEmail())) {
            logger.warn("Registration failed: Email {} is already in use.", customer.getEmail());
            throw new IllegalStateException("Error: Email is already in use!");
        }

        String password = customer.getPassword();
        if (password != null) {
            customer.setPassword(passwordEncoder.encode(password));
        }

        // Default tier
        customer.setBuyerCategory(Customer.BuyerCategory.NOT_VERIFIED);

        Customer savedCustomer = customerRepository.save(customer);
        //logger.info("Customer registered successfully with ID: {}", savedCustomer.getId());

        return savedCustomer;
    }

    /**
     * Authenticates a customer.
     */
    public Customer loginCustomer(String email, String password)
            throws UserPrincipalNotFoundException, CredentialNotFoundException {

        if (email == null || password == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        //logger.info("Attempting to login customer with email: {}", email);

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Login failed: User not found for email: {}", email);
                    return new UserPrincipalNotFoundException("User does not exist.");
                });

        if (!passwordEncoder.matches(password, customer.getPassword())) {
            logger.warn("Login failed: Wrong password for email: {}", email);
            throw new CredentialNotFoundException("Wrong password.");
        }

        // 🔥 Compute tier on login
        attachBuyerCategory(customer);

        //logger.info("Customer logged in successfully: {}", email);
        return customer;
    }

    /* =====================================================
       🔹 TIER COMPUTATION LOGIC (BACKEND SOURCE OF TRUTH)
       ===================================================== */

    public void attachBuyerCategory(Customer customer) {
        if (customer == null || customer.getId() == null) return;

        double totalSpent = calculateTotalSpent(customer.getId());

        Customer.BuyerCategory category;

        if (totalSpent >= 100000) {
            category = Customer.BuyerCategory.ELITE;
        } else if (totalSpent >= 10000) {
            category = Customer.BuyerCategory.PRIME;
        } else if (totalSpent >= 1000) {
            category = Customer.BuyerCategory.VERIFIED;
        } else {
            category = Customer.BuyerCategory.NOT_VERIFIED;
        }

        customer.setBuyerCategory(category);
        customer.setTotalSpent(totalSpent);

        customerRepository.save(customer);

        //logger.info(
        //        "Computed buyer category for customer {} → {} (₹{})",
        //        customer.getId(),
        //        category,
        //        totalSpent
        //);
    }

    private double calculateTotalSpent(String customerId) {
        if (customerId == null) return 0.0;

        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);

        List<Transaction> completed =
                transactionRepository.findByCustomerIdAndStatusAndCreatedAtAfter(
                        customerId,
                        "COMPLETED",
                        twelveMonthsAgo
                );

        if (completed == null) return 0.0;

        return completed.stream()
                .mapToDouble(t -> t != null && t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
    }
    public void resetPassword(String email, String newPassword) throws UserPrincipalNotFoundException {
        if (email == null || newPassword == null) {
            throw new IllegalArgumentException("Email and new password are required");
        }
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new UserPrincipalNotFoundException("User not found for password reset."));

        String encodedPassword = passwordEncoder.encode(newPassword);
        customer.setPassword(encodedPassword);
        customerRepository.save(customer);

        //logger.info("Password successfully reset for email: {}", email);
    }
}
