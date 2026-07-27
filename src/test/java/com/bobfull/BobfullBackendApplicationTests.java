package com.bobfull;

import com.bobfull.sharedtable.repository.SharedTableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "jwt.secret=context-loads-test-secret-key-please-keep-this-long-enough",
        "jwt.access-token-expiration-seconds=3600",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class BobfullBackendApplicationTests {

	@MockitoBean
	private SharedTableRepository sharedTableRepository;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMappingContext;

	@Test
	void contextLoads() {
	}

}
