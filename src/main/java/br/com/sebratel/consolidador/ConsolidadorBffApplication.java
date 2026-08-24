package br.com.sebratel.consolidador;

import br.com.sebratel.consolidador.report.AutomationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AutomationProperties.class)
public class ConsolidadorBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsolidadorBffApplication.class, args);
	}

}
