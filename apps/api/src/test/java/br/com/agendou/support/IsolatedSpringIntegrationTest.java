package br.com.agendou.support;

import org.springframework.test.annotation.DirtiesContext;

/** Evict each class context so pools and schedulers cannot outlive its test database. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IsolatedSpringIntegrationTest {
}
