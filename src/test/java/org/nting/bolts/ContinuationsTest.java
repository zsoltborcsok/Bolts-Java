package org.nting.bolts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nting.bolts.Continuations.doLocking;
import static org.nting.bolts.Continuations.onMiddle;
import static org.nting.bolts.Continuations.onSuccess;
import static org.nting.bolts.Continuations.onSuccessWithResult;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Test;
import org.nting.bolts.Continuations.ContinuationLockable;

public class ContinuationsTest {

    @SuppressWarnings("unchecked")
    @Test
    public void testOnSuccess() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new CustomException());
        Task<Integer> cancelled = Task.cancelled();

        Continuation<Void, Void> checkResult = task -> {
            assertTrue(task.isCompleted());
            assertNull(task.getResult());
            assertFalse(task.isFaulted());
            assertFalse(task.isCancelled());
            return null;
        };

        Consumer<Integer> onSuccess = mock(Consumer.class);
        Consumer<Exception> onError = mock(Consumer.class);
        Runnable onCancel = mock(Runnable.class);
        Runnable onFinally = mock(Runnable.class);

        complete.continueWith(onSuccess(onSuccess).onError(onError).onCancel(onCancel).onFinally(onFinally).build())
                .continueWith(checkResult);
        verify(onSuccess).accept(5);
        verify(onError, never()).accept(any());
        verify(onCancel, never()).run();
        verify(onFinally).run();
        reset(onSuccess, onError, onCancel, onFinally);

        error.continueWith(onSuccess(onSuccess).onError(onError).onCancel(onCancel).onFinally(onFinally).build())
                .continueWith(checkResult);
        verify(onSuccess, never()).accept(any());
        verify(onError).accept(any(CustomException.class));
        verify(onCancel, never()).run();
        verify(onFinally).run();
        reset(onSuccess, onError, onCancel, onFinally);

        cancelled.continueWith(onSuccess(onSuccess).onError(onError).onCancel(onCancel).onFinally(onFinally).build())
                .continueWith(checkResult);
        verify(onSuccess, never()).accept(any());
        verify(onError, never()).accept(any());
        verify(onCancel).run();
        verify(onFinally).run();
        reset(onSuccess, onError, onCancel, onFinally);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOnMiddle() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new CustomException());
        Task<Integer> cancelled = Task.cancelled();

        Consumer<Task<Integer>> consumer = mock(Consumer.class);

        complete.continueWithTask(onMiddle(consumer)).continueWith(task -> {
            assertTrue(task.isCompleted());
            assertEquals((Integer) 5, task.getResult());
            assertFalse(task.isFaulted());
            assertFalse(task.isCancelled());
            return null;
        });
        verify(consumer).accept(complete);
        reset(consumer);

        error.continueWithTask(onMiddle(consumer)).continueWith(task -> {
            assertTrue(task.isCompleted());
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof CustomException);
            assertFalse(task.isCancelled());
            return null;
        });
        verify(consumer).accept(error);
        reset(consumer);

        cancelled.continueWithTask(onMiddle(consumer)).continueWith(task -> {
            assertTrue(cancelled.isCompleted());
            assertFalse(cancelled.isFaulted());
            assertTrue(cancelled.isCancelled());
            return null;
        });
        verify(consumer).accept(cancelled);
        reset(consumer);
    }

    @Test
    public void testDoLocking() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new CustomException());
        Task<Integer> cancelled = Task.cancelled();

        ContinuationLockable lockable = spy(new ContinuationLockableImpl());

        Continuation<Integer, Void> checkNotLocked = task -> {
            assertFalse(lockable.isLocked());
            return null;
        };

        Continuation<Integer, Task<Integer>> unlock = doLocking(lockable);
        complete.continueWithTask(onMiddle(i -> assertTrue(lockable.isLocked()))).continueWithTask(unlock)
                .continueWith(checkNotLocked);
        verify(lockable).lock();
        verify(lockable).unLock();
        reset(lockable);

        unlock = doLocking(lockable);
        error.continueWithTask(onMiddle(i -> assertTrue(lockable.isLocked()))).continueWithTask(unlock)
                .continueWith(checkNotLocked);
        verify(lockable).lock();
        verify(lockable).unLock();
        reset(lockable);

        unlock = doLocking(lockable);
        cancelled.continueWithTask(onMiddle(i -> assertTrue(lockable.isLocked()))).continueWithTask(unlock)
                .continueWith(checkNotLocked);
        verify(lockable).lock();
        verify(lockable).unLock();
        reset(lockable);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOnSuccessWithResult() {
        Point point = new Point(0, 0);

        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new CustomException());
        Task<Integer> cancelled = Task.cancelled();

        BiConsumer<Integer, Capture<Point>> onSuccess = mock(BiConsumer.class);
        Function<Exception, Boolean> onError = mock(Function.class);
        when(onError.apply(any())).thenReturn(true);
        Runnable onCancel = mock(Runnable.class);

        complete.continueWithTask(onSuccessWithResult(onSuccess, point).onError(onError).onCancel(onCancel).build())
                .continueWithTask(task -> {
                    assertTrue(task.isCompleted());
                    assertEquals(point, task.getResult());
                    assertFalse(task.isFaulted());
                    assertFalse(task.isCancelled());
                    return null;
                });
        verify(onSuccess).accept(eq(5), argThat(capture -> capture.get() == point));
        verify(onError, never()).apply(any());
        verify(onCancel, never()).run();
        reset(onSuccess, onError, onCancel);

        when(onError.apply(any())).thenReturn(true);
        error.continueWithTask(onSuccessWithResult(onSuccess, point).onError(onError).onCancel(onCancel).build())
                .continueWithTask(task -> {
                    assertTrue(task.isCompleted());
                    assertTrue(task.isFaulted());
                    assertTrue(task.getError() instanceof CustomException);
                    assertFalse(task.isCancelled());
                    return null;
                });
        verify(onSuccess, never()).accept(any(), any());
        verify(onError).apply(any());
        verify(onCancel, never()).run();
        reset(onSuccess, onError, onCancel);

        cancelled.continueWithTask(onSuccessWithResult(onSuccess, point).onError(onError).onCancel(onCancel).build())
                .continueWithTask(task -> {
                    assertTrue(task.isCompleted());
                    assertFalse(task.isFaulted());
                    assertTrue(task.isCancelled());
                    return null;
                });
        verify(onSuccess, never()).accept(any(), any());
        verify(onError, never()).apply(any());
        verify(onCancel).run();
        reset(onSuccess, onError, onCancel);
    }

    @Test
    public void testOnSuccessWithResult_SampleScenarios() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new CustomException());

        // Do some change on the preconfigured result and propagate/forward it.
        Point point = new Point(0, 0);
        complete.continueWithTask(
                Continuations.<Integer, Point> onSuccessWithResult((x, refP) -> refP.get().move(x, 0), point).build())
                .continueWithTask(task -> {
                    assertEquals(point, task.getResult());
                    return null;
                });
        assertEquals(5, point.x);

        // Replace the preconfigured result and propagate/forward this new one.
        point.setLocation(0, 0);
        complete.continueWithTask(Continuations
                .<Integer, Point> onSuccessWithResult((x, refP) -> refP.set(new Point(x, 2 * x)), point).build())
                .continueWithTask(task -> {
                    assertNotEquals(point, task.getResult());
                    assertEquals(5, task.getResult().x);
                    assertEquals(10, task.getResult().y);
                    return null;
                });
        assertEquals(0, point.x);
        assertEquals(0, point.y);

        // On error still propagate/forward the preconfigured result (without changing it).
        point.setLocation(0, 0);
        error.continueWithTask(
                Continuations.<Integer, Point> onSuccessWithResult((x, refP) -> refP.get().move(x, 0), point)
                        .onError(e -> false).build())
                .continueWithTask(task -> {
                    assertEquals(point, task.getResult());
                    return null;
                });
        assertEquals(0, point.x);
    }

    private static class CustomException extends RuntimeException {
    }

    private static class ContinuationLockableImpl implements ContinuationLockable {

        private boolean locked = false;

        @Override
        public void lock() {
            locked = true;
        }

        @Override
        public void unLock() {
            locked = false;
        }

        @Override
        public boolean isLocked() {
            return locked;
        }
    }
}