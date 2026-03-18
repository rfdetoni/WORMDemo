package br.com.worm.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class WormUseDemoApplicationTests {

    @Test
    void contextLoads() {
        assertNotNull(Book.find);
        assertNotNull(Author.find);
    }
}
