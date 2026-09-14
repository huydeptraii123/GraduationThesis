package com.slatcut.cutting;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Tách thành class riêng thay vì nested class trong AbstractIntegrationTest: nested @Configuration
 * không private sẽ bị Spring coi là cấu hình chính của test (từ Spring Framework 7.1) và chặn mất
 * @SpringBootConfiguration của ứng dụng, còn nested private thì chính annotation @Import trên lớp
 * bao ngoài lại không tham chiếu tới được.
 */
@TestConfiguration(proxyBeanMethods = false)
class MySqlTestcontainerConfig {

    // Container khai báo dạng bean (không dùng @Testcontainers/@Container): Spring quản vòng đời theo
    // context, mà context được cache dùng chung cho mọi test class nên container chỉ khởi động 1 lần
    // cho cả lượt chạy. Dùng @Container thì JUnit tắt container ngay sau test class đầu tiên, trong
    // khi context cache vẫn trỏ vào cổng cũ -> các class sau chết vì mất kết nối.
    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.0");
    }
}
