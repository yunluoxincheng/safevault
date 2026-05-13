package org.ttt.safevaultbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafevaultBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafevaultBackendApplication.class, args);
    }

}
