package com.bobfull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:bobfull-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"jwt.secret=bobfull-context-load-test-secret-key-please-keep-long",
		"jwt.access-token-expiration-seconds=3600",
		"portone.api-secret=portone-context-load-test-api-secret",
		"portone.store-id=portone-context-load-test-store-id"
})
class BobfullBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
