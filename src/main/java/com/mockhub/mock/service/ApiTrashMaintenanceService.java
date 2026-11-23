package com.mockhub.mock.service;
import com.mockhub.mock.repository.ApiRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Conditional expiration purge: no simulated user or global administrator principal. Physical GC is separate and retryable. */
@Service
public class ApiTrashMaintenanceService {
    private final ApiRepository apis;
    public ApiTrashMaintenanceService(ApiRepository apis) { this.apis=apis; }
    @Scheduled(fixedDelayString="${mock.api.trash-cleanup-interval-ms:3600000}",initialDelayString="${mock.api.trash-cleanup-interval-ms:3600000}")
    public void purgeExpired() { apis.purgeExpired(100); }
}
