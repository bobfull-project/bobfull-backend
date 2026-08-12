package com.bobfull;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:bobfull-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"jwt.secret=bobfull-context-load-test-secret-key-please-keep-long",
		"jwt.access-token-expiration-seconds=1800",
		"portone.api-secret=portone-context-load-test-api-secret",
		"portone.store-id=portone-context-load-test-store-id",
		"portone.webhook-secret=d2hzZWNfY29udGV4dC10ZXN0",
		"aws.region=ap-northeast-2",
		"aws.s3.image-bucket=bobfull-test-image-bucket"
})
class BobfullBackendApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBeansOfType(NewTopic.class)).isEmpty();
	}

}
