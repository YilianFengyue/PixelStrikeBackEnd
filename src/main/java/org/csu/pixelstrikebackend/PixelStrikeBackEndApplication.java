package org.csu.pixelstrikebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("org.csu.pixelstrikebackend.lobby.mapper")
public class PixelStrikeBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelStrikeBackEndApplication.class, args);
    }

}
