package org.nting.bolts;

public class Tasks {

    /**
     * This method waits for the result of the provided task and return with that result. It stops the execution of the
     * current thread until the task is completed. So, it could cause dead lock if not used carefully. GWT incompatible.
     * 
     * @throws TaskCompletedWithErrorException
     *             when the provided task is completed with an error.
     * @throws TaskCompletedWithCancellationException
     *             when the provided task is completed with cancellation.
     */
    public static <T> T await(Task<T> task) {
        final Object lock = new Object();

        task.continueWith((Continuation<T, Void>) task1 -> {
            synchronized (lock) {
                lock.notifyAll();
            }
            return null;
        });

        try {
            synchronized (lock) {
                while (!task.isCompleted()) {
                    lock.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (task.isFaulted()) {
            throw new TaskCompletedWithErrorException(task.getError());
        } else if (task.isCancelled()) {
            throw new TaskCompletedWithCancellationException();
        } else {
            return task.getResult();
        }
    }

    public static class TaskCompletedWithErrorException extends RuntimeException {

        public TaskCompletedWithErrorException(Exception cause) {
            super(cause);
        }

        @Override
        public synchronized Exception getCause() {
            return (Exception) super.getCause();
        }
    }

    public static class TaskCompletedWithCancellationException extends RuntimeException {
    }
}
