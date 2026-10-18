package com.gameflix.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The starting point of the application: running this class starts the whole service.
 *
 * <p>Where it sits: before any request arrives. {@code main} starts Spring, which then finds every
 * class in this package marked {@code @RestController}, {@code @Service}, {@code @Configuration},
 * etc., creates one object of each, connects them through their constructors, connects to MySQL,
 * and starts the built-in Tomcat web server on port 8080 (SPEC.md section 1).
 */
// @SpringBootApplication: turns on Spring Boot's automatic setup and tells Spring to scan this
// package (com.gameflix.auth) and its sub-packages for the classes above.
@SpringBootApplication
public class GameflixAuthServiceApplication {

	/**
	 * Java calls this method first when the program starts.
	 *
	 * @param args command-line arguments, passed on to Spring (none are needed)
	 */
	public static void main(String[] args) {
		// Start Spring and the web server; this keeps running until the app is stopped.
		SpringApplication.run(GameflixAuthServiceApplication.class, args);
	}

}
