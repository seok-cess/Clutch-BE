package com.clutch.lolesports.source;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 현재 선택된 외부 데이터 소스와 전환 잠금을 관리한다.
 *
 * 폴링은 읽기 잠금을 잡은 채 한 소스만 사용하고, 소스 전환은 쓰기 잠금으로 진행한다.
 * 따라서 전환 전후의 응답이 같은 캐시에 섞이지 않는다.
 */
@Component
public class ExternalSourceState {

    private final AtomicReference<ExternalSourceMode> mode;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public ExternalSourceState(ExternalSourceProperties properties) {
        this.mode = new AtomicReference<>(properties.initialMode());
    }

    public ExternalSourceMode mode() {
        return mode.get();
    }

    public <T> T withReadLock(Supplier<T> action) {
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void withReadLock(Runnable action) {
        lock.readLock().lock();
        try {
            action.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T withWriteLock(Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 쓰기 잠금을 보유한 호출자만 현재 모드를 바꾼다. */
    public void changeMode(ExternalSourceMode target) {
        if (!lock.isWriteLockedByCurrentThread()) {
            throw new IllegalStateException("외부 소스 모드는 전환 잠금 안에서만 변경할 수 있다");
        }
        mode.set(target);
    }
}
