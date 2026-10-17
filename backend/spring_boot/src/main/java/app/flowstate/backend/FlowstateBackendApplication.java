package app.flowstate.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class FlowstateBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowstateBackendApplication.class, args);
    }
}
