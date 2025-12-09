package com.fampay.scheduler.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class LeaderLock {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public boolean acquire(String lockName, int timeoutSeconds) {
//      Long res = (Long) em.createNativeQuery("SELECT GET_LOCK(:name, :timeout)")
//          .setParameter("name", lockName)
//          .setParameter("timeout", timeoutSeconds)
//          .getSingleResult();
//      return res != null && res == 1;
      return true;
    }

    @Transactional
    public boolean release(String lockName) {
      Long res = (Long) em.createNativeQuery("SELECT RELEASE_LOCK(:name)")
          .setParameter("name", lockName)
          .getSingleResult();
      return res != null && res == 1;
    }
  }
