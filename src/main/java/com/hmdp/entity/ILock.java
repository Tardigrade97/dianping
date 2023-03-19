package com.hmdp.entity;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 持有锁的超时时间，过期后自动释放
     * @return true:获取锁成功，false:获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
