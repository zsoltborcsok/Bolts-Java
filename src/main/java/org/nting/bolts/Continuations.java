package org.nting.bolts;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Continuations {

    /**
     * It is planned to be used as a parameter for Task.continueWith(...) method, which adds a synchronous continuation
     * to the given task and which cannot forward the cancellation and errors. So, basically this should be the last
     * item of the continuation chain (as it doesn't propagate any information to the next continuation item).
     */
    public static <T> ContinuationBuilder<T> onSuccess(Consumer<T> onSuccess) {
        return new ContinuationBuilder<>(onSuccess);
    }

    /**
     * It is planned to be used as a parameter for Task.continueWithTask(...) method. It forwards the error,
     * cancellation or the result without any change. It just gives opportunity to do some extra work in the middle of
     * the continuation chain.
     */
    public static <T> Continuation<T, Task<T>> onMiddle(Consumer<Task<T>> consumer) {
        return task -> {
            consumer.accept(task);

            if (task.isFaulted()) {
                return Task.forError(task.getError());
            } else if (task.isCancelled()) {
                return Task.cancelled();
            } else {
                return Task.forResult(task.getResult());
            }
        };
    }

    /**
     * It just creates a 'middle' continuation with built-in lock-unlock mechanism. See {@link #onMiddle(Consumer)}.
     */
    public static <T> Continuation<T, Task<T>> doLocking(ContinuationLockable lockable) {
        lockable.lock();
        return onMiddle(task -> lockable.unLock());
    }

    /**
     * It is planned to be used as a parameter for Task.continueWithTask(...) method. It forwards the error,
     * cancellation and the preconfigured result. One usage scenario could be that the result of the previous task just
     * gives some configuration to an existing object and we want to propagate/forward that object for the next
     * continuation.
     */
    public static <T, R> ContinuationWithResultBuilder<T, R> onSuccessWithResult(BiConsumer<T, Capture<R>> onSuccess,
            R continuationResult) {
        return new ContinuationWithResultBuilder<>(onSuccess, continuationResult);
    }

    public static class ContinuationBuilder<TTaskResult> {

        private final Consumer<TTaskResult> onSuccess;
        private Runnable onCancel = doNothing();
        private Consumer<Exception> onError = emptyConsumer();
        private Runnable onFinally = doNothing();

        public ContinuationBuilder(Consumer<TTaskResult> onSuccess) {
            this.onSuccess = onSuccess;
        }

        public ContinuationBuilder<TTaskResult> onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        public ContinuationBuilder<TTaskResult> onFinally(Runnable onFinally) {
            this.onFinally = onFinally;
            return this;
        }

        public ContinuationBuilder<TTaskResult> onError(Consumer<Exception> onError) {
            this.onError = onError;
            return this;
        }

        public Continuation<TTaskResult, Void> build() {
            return task -> {
                try {
                    if (task.isCancelled()) {
                        onCancel.run();
                    } else if (task.isFaulted()) {
                        onError.accept(task.getError());
                    } else if (task.isCompleted()) {
                        onSuccess.accept(task.getResult());
                    }
                } catch (Throwable e) {
                    Logger.getLogger(onSuccess.getClass().getName()).log(Level.SEVERE, e.getMessage(), e);
                } finally {
                    onFinally.run();
                }
                return null;
            };
        }
    }

    public interface ContinuationLockable {

        void lock();

        void unLock();

        boolean isLocked();
    }

    public static class ContinuationWithResultBuilder<TTaskResult, TContinuationResult> {

        private final BiConsumer<TTaskResult, Capture<TContinuationResult>> onSuccess;
        private final Capture<TContinuationResult> continuationResult;
        private Runnable onCancel = doNothing();
        private Function<Exception, Boolean> onError = e -> true;

        public ContinuationWithResultBuilder(BiConsumer<TTaskResult, Capture<TContinuationResult>> onSuccess,
                TContinuationResult continuationResult) {
            this.onSuccess = onSuccess;
            this.continuationResult = new Capture<>(continuationResult);
        }

        public ContinuationWithResultBuilder<TTaskResult, TContinuationResult> onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        /**
         * Function should return true to indicate that the error should be propagated/forwarded; otherwise the
         * configured continuationResult will be propagated/forwarded (the first one is the default).
         */
        public ContinuationWithResultBuilder<TTaskResult, TContinuationResult> onError(
                Function<Exception, Boolean> onError) {
            this.onError = onError;
            return this;
        }

        public Continuation<TTaskResult, Task<TContinuationResult>> build() {
            return task -> {
                if (task.isCancelled()) {
                    onCancel.run();
                    return Task.cancelled();
                } else if (task.isFaulted()) {
                    if (onError.apply(task.getError())) {
                        return Task.forError(task.getError());
                    } else {
                        return Task.forResult(continuationResult.get());
                    }
                } else {
                    onSuccess.accept(task.getResult(), continuationResult);
                    return Task.forResult(continuationResult.get());
                }
            };
        }
    }

    public static Runnable doNothing() {
        return () -> {
        };
    }

    public static <T> Consumer<T> emptyConsumer() {
        return t -> {
        };
    }
}
