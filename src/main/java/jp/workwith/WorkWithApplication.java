package jp.workwith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 「ワークwith」バックエンドの起動クラスです。
 */
@SpringBootApplication
@EnableScheduling
public class WorkWithApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkWithApplication.class, args);
    }
}
