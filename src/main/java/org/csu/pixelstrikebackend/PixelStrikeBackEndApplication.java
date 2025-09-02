package org.csu.pixelstrikebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PixelStrikeBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelStrikeBackEndApplication.class, args);
    }

}
