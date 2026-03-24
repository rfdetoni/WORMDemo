package br.com.worm.demo;

import br.com.liviacare.worm.annotation.EnableWorm;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableWorm
@EnableJpaRepositories(basePackages = "br.com.worm.demo")
public class WormUseDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WormUseDemoApplication.class, args);
    }
}
