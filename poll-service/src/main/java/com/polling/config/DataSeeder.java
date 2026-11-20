package com.polling.config;

import com.polling.entity.Poll;
import com.polling.entity.User;
import com.polling.repository.PollRepository;
import com.polling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the database with sample users and polls in the 'dev' profile.
 * Idempotent — skips seeding if data already exists.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PollRepository pollRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }

        // Seed users
        User alice = seedUser("Alice Smith", "alice@example.com", "password123");
        User bob   = seedUser("Bob Jones",  "bob@example.com",   "password123");

        // Seed polls (only if no polls exist)
        if (pollRepository.count() == 0) {
            Poll poll1 = Poll.builder()
                    .creator(alice)
                    .question("What is your favorite programming language?")
                    .options(List.of(
                            new Poll.PollOption(UUID.randomUUID().toString(), "Java"),
                            new Poll.PollOption(UUID.randomUUID().toString(), "Python"),
                            new Poll.PollOption(UUID.randomUUID().toString(), "Go"),
                            new Poll.PollOption(UUID.randomUUID().toString(), "Rust")
                    ))
                    .startTime(Instant.now())
                    .endTime(Instant.now().plus(7, ChronoUnit.DAYS))
                    .status(Poll.Status.ACTIVE)
                    .build();

            Poll poll2 = Poll.builder()
                    .creator(bob)
                    .question("Best pet for a software engineer?")
                    .options(List.of(
                            new Poll.PollOption(UUID.randomUUID().toString(), "Cat"),
                            new Poll.PollOption(UUID.randomUUID().toString(), "Dog"),
                            new Poll.PollOption(UUID.randomUUID().toString(), "Rubber Duck")
                    ))
                    .startTime(Instant.now())
                    .endTime(Instant.now().plus(3, ChronoUnit.DAYS))
                    .status(Poll.Status.ACTIVE)
                    .build();

            pollRepository.saveAll(List.of(poll1, poll2));
            log.info("Seeded 2 sample polls");
        }

        log.info("Dev data seeding complete. Login with alice@example.com / password123");
    }

    private User seedUser(String name, String email, String password) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .name(name)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(User.Role.USER)
                    .build();
            User saved = userRepository.save(user);
            log.info("Seeded user: {}", email);
            return saved;
        });
    }
}
