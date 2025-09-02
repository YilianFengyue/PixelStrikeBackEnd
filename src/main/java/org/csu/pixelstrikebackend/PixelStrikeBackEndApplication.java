package org.csu.pixelstrikebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.csu.pixelstrikebackend.mapper")
public class PixelStrikeBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelStrikeBackEndApplication.class, args);
    }

}
