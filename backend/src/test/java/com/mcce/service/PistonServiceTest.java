package com.mcce.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class PistonServiceTest {

	@Test
	void rewritesJavaOopSourceToSingleMainClassForJudge0() {
		PistonService service = new PistonService(
			new RestTemplateBuilder(),
			"https://ce.judge0.com/submissions?base64_encoded=false&wait=true",
			1000
		);

		String source = """
			package com.example;

			public class Student {
				private final String name;

				public Student(String name) {
					this.name = name;
				}

				public String getName() {
					return name;
				}
			}

			public class App {
				public static void main(String[] args) {
					Student student = new Student("Alice");
					System.out.println(student.getName());
				}
			}
			""";

		String processed = service.preprocessSourceCode("java", source);

		assertFalse(processed.contains("package com.example;"));
		assertTrue(processed.contains("class Student"));
		assertFalse(processed.contains("public class Student"));
		assertTrue(processed.contains("public class Main"));
		assertFalse(processed.contains("public class App"));
		assertTrue(processed.contains("Student student = new Student(\"Alice\");"));
	}
}
