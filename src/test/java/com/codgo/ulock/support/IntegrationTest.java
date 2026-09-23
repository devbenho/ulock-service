package com.codgo.ulock.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for full-stack tests against a real PostgreSQL. All subclasses share one Spring context and
 * one container; tests isolate themselves by creating their own tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainer.class, Api.class})
public abstract class IntegrationTest {

    @Autowired
    protected Api api;
}
