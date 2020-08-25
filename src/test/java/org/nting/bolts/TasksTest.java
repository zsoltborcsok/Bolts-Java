package org.nting.bolts;

import static org.junit.Assert.assertEquals;
import static org.nting.bolts.Tasks.await;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.nting.bolts.Tasks.TaskCompletedWithCancellationException;
import org.nting.bolts.Tasks.TaskCompletedWithErrorException;

public class TasksTest {

    private ScheduledExecutorService executorService;

    @Before
    public void setUp() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Test
    public void testAwait() {
        assertEquals((Integer) 44, await(async(44)));
    }

    @Test(expected = TaskCompletedWithCancellationException.class)
    public void testAwait_Cancel() {
        await(asyncCancel());
    }

    @Test(expected = NoSuchFieldException.class)
    public void testAwait_Error() throws Exception {
        try {
            await(asyncError(new NoSuchFieldException()));
        } catch (TaskCompletedWithErrorException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testAwait_Sync() {
        assertEquals((Integer) 44, await(Task.forResult(44)));
    }

    @Test(expected = TaskCompletedWithCancellationException.class)
    public void testAwait_Cancel_Sync() {
        await(Task.cancelled());
    }

    @Test(expected = NoSuchFieldException.class)
    public void testAwait_Error_Sync() throws Exception {
        try {
            await(Task.forError(new NoSuchFieldException()));
        } catch (TaskCompletedWithErrorException e) {
            throw e.getCause();
        }
    }

    private <T> Task<T> async(T result) {
        Task<T>.TaskCompletionSource taskCompletionSource = Task.create();
        executorService.schedule(() -> taskCompletionSource.setResult(result), 200, TimeUnit.MILLISECONDS);
        return taskCompletionSource.getTask();
    }

    private <T> Task<T> asyncError(Exception error) {
        Task<T>.TaskCompletionSource taskCompletionSource = Task.create();
        executorService.schedule(() -> taskCompletionSource.setError(error), 200, TimeUnit.MILLISECONDS);
        return taskCompletionSource.getTask();
    }

    private <T> Task<T> asyncCancel() {
        Task<T>.TaskCompletionSource taskCompletionSource = Task.create();
        executorService.schedule(taskCompletionSource::setCancelled, 200, TimeUnit.MILLISECONDS);
        return taskCompletionSource.getTask();
    }
}
