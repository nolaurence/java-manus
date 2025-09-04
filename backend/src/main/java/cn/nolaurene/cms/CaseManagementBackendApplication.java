package cn.nolaurene.cms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({"cn.nolaurene.cms.dal.enhance.mapper", "cn.nolaurene.cms.dal.mapper"})
@SpringBootApplication
public class CaseManagementBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CaseManagementBackendApplication.class, args);
	}

}
