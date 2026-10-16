package br.car.dsp_geo_file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DspGeoFileGenerationApplication {

	/**
	 * Exits the JVM after JobRunner — required so the core one-shot container
	 * (docker compose run) does not stay "stuck" waiting for a process that has no work left.
	 */
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(DspGeoFileGenerationApplication.class, args)));
	}

}
