package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.system.repository.AdminSafetyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;

@Service
public class AdminSafetyService {
    @Autowired private AdminSafetyRepository repository;
    public void acquireWriteLock() { repository.acquireWriteLock(); }
    public List<String> effectiveAdminTeams(String userId) { return repository.findEffectiveAdminTeamIds(userId); }
    public int effectiveTeamAdminCount(String teamId) { return repository.countEffectiveTeamAdmins(teamId); }
    public void validateTeams(Collection<String> affectedTeamIds) {
        for (String teamId : affectedTeamIds) {
            if (repository.countEffectiveTeamAdmins(teamId) < 1) {
                throw new BizException(40901, "团队必须保留至少一位有效管理员：" + teamId);
            }
        }
    }
    public void validateSuperAdmins() {
        if (repository.countEffectiveSuperAdmins() < 1) {
            throw new BizException(40902, "系统必须保留至少一位有效超级管理员");
        }
    }
    public void detachDeletedUser(String userId) { repository.detachDeletedUser(userId); }
}
