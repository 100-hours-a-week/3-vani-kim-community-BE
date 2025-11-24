package com.vani.week4.backend.global.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3설정 - AWS 배포 환경용 (IAM Role 사용)
 * EC2 Instance Profile, ECS Task Role, Lambda Execution Role 등을 자동으로 사용
 * @author vani
 * @since 10/16/25
 */
@Configuration
@Profile({"prod", "loadtest"})
public class AwsS3Config {

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}