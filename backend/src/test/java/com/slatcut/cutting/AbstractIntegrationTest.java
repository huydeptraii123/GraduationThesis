package com.slatcut.cutting;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Import(MySqlTestcontainerConfig.class)
public abstract class AbstractIntegrationTest {
}
