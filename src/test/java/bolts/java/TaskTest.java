/*
 * Copyright (c) 2014, Facebook, Inc. All rights reserved. This source code is licensed under the BSD-style license
 * found in the LICENSE file in the root directory of this source tree. An additional grant of patent rights can be
 * found in the PATENTS file in the same directory.
 */
package bolts.java;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;

import junit.framework.TestCase;

public class TaskTest extends TestCase {
    private void runTaskTest(Callable<Task<?>> callable) {
        try {
            Task<?> task = callable.call();
            if (task.isFaulted()) {
                Exception error = task.getError();
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                throw new RuntimeException(error);
            } else if (task.isCancelled()) {
                throw new RuntimeException(new CancellationException());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void testPrimitives() {
        Task<Integer> complete = Task.forResult(5);
        Task<Integer> error = Task.forError(new RuntimeException());
        Task<Integer> cancelled = Task.cancelled();

        Assert.assertTrue(complete.isCompleted());
        Assert.assertEquals(5, complete.getResult().intValue());
        Assert.assertFalse(complete.isFaulted());
        Assert.assertFalse(complete.isCancelled());

        Assert.assertTrue(error.isCompleted());
        Assert.assertTrue(error.getError() instanceof RuntimeException);
        Assert.assertTrue(error.isFaulted());
        Assert.assertFalse(error.isCancelled());

        Assert.assertTrue(cancelled.isCompleted());
        Assert.assertFalse(cancelled.isFaulted());
        Assert.assertTrue(cancelled.isCancelled());
    }

    public void testSynchronousContinuation() {
        final Task<Integer> complete = Task.forResult(5);
        final Task<Integer> error = Task.forError(new RuntimeException());
        final Task<Integer> cancelled = Task.cancelled();

        complete.continueWith(new Continuation<Integer, Void>() {
            public Void then(Task<Integer> task) {
                Assert.assertEquals(complete, task);
                Assert.assertTrue(task.isCompleted());
                Assert.assertEquals(5, task.getResult().intValue());
                Assert.assertFalse(task.isFaulted());
                Assert.assertFalse(task.isCancelled());
                return null;
            }
        });

        error.continueWith(new Continuation<Integer, Void>() {
            public Void then(Task<Integer> task) {
                Assert.assertEquals(error, task);
                Assert.assertTrue(task.isCompleted());
                Assert.assertTrue(task.getError() instanceof RuntimeException);
                Assert.assertTrue(task.isFaulted());
                Assert.assertFalse(task.isCancelled());
                return null;
            }
        });

        cancelled.continueWith(new Continuation<Integer, Void>() {
            public Void then(Task<Integer> task) {
                Assert.assertEquals(cancelled, task);
                Assert.assertTrue(cancelled.isCompleted());
                Assert.assertFalse(cancelled.isFaulted());
                Assert.assertTrue(cancelled.isCancelled());
                return null;
            }
        });
    }

    public void testSynchronousChaining() {
        Task<Integer> first = Task.forResult(1);
        Task<Integer> second = first.continueWith(new Continuation<Integer, Integer>() {
            public Integer then(Task<Integer> task) {
                return 2;
            }
        });
        Task<Integer> third = second.continueWithTask(new Continuation<Integer, Task<Integer>>() {
            public Task<Integer> then(Task<Integer> task) {
                return Task.forResult(3);
            }
        });
        Assert.assertTrue(first.isCompleted());
        Assert.assertTrue(second.isCompleted());
        Assert.assertTrue(third.isCompleted());
        Assert.assertEquals(1, first.getResult().intValue());
        Assert.assertEquals(2, second.getResult().intValue());
        Assert.assertEquals(3, third.getResult().intValue());
    }

    public void testBackgroundCall() {
        runTaskTest(new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return Task.call(new Callable<Integer>() {
                    public Integer call() throws Exception {
                        Thread.sleep(100);
                        return 5;
                    }
                }).continueWith(new Continuation<Integer, Void>() {
                    public Void then(Task<Integer> task) {
                        Assert.assertEquals(5, task.getResult().intValue());
                        return null;
                    }
                });
            }
        });
    }

    public void testBackgroundCallWaiting() throws Exception {
        Task<Integer> task = Task.call(new Callable<Integer>() {
            public Integer call() throws Exception {
                Thread.sleep(100);
                return 5;
            }
        });
        Assert.assertTrue(task.isCompleted());
        Assert.assertEquals(5, task.getResult().intValue());
    }

    public void testBackgroundCallWaitingOnError() throws Exception {
        Task<Integer> task = Task.call(new Callable<Integer>() {
            public Integer call() throws Exception {
                Thread.sleep(100);
                throw new RuntimeException();
            }
        });
        Assert.assertTrue(task.isCompleted());
        Assert.assertTrue(task.isFaulted());
    }

    public void testBackgroundCallWaitOnCancellation() throws Exception {
        Task<Integer> task = Task.call(new Callable<Integer>() {
            public Integer call() throws Exception {
                Thread.sleep(100);
                return 5;
            }
        }).continueWithTask(new Continuation<Integer, Task<Integer>>() {

            public Task<Integer> then(Task<Integer> task) {
                return Task.cancelled();
            }
        });
        Assert.assertTrue(task.isCompleted());
        Assert.assertTrue(task.isCancelled());
    }

    public void testBackgroundError() {
        runTaskTest(new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return Task.call(new Callable<Integer>() {
                    public Integer call() throws Exception {
                        throw new IllegalStateException();
                    }
                }).continueWith(new Continuation<Integer, Void>() {
                    public Void then(Task<Integer> task) {
                        Assert.assertTrue(task.isFaulted());
                        Assert.assertTrue(task.getError() instanceof IllegalStateException);
                        return null;
                    }
                });
            }
        });
    }

    public void testWhenAllSuccess() {
        runTaskTest(new Callable<Task<?>>() {
            @Override
            public Task<?> call() throws Exception {
                final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
                for (int i = 0; i < 20; i++) {
                    Task<Void> task = Task.call(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            Thread.sleep((long) (Math.random() * 1000));
                            return null;
                        }
                    });
                    tasks.add(task);
                }
                return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
                    @Override
                    public Void then(Task<Void> task) {
                        Assert.assertTrue(task.isCompleted());
                        Assert.assertFalse(task.isFaulted());
                        Assert.assertFalse(task.isCancelled());

                        for (Task<Void> t : tasks) {
                            Assert.assertTrue(t.isCompleted());
                        }
                        return null;
                    }
                });
            }
        });
    }

    public void testWhenAllOneError() {
        final Exception error = new RuntimeException("This task failed.");

        runTaskTest(new Callable<Task<?>>() {
            @Override
            public Task<?> call() throws Exception {
                final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
                for (int i = 0; i < 20; i++) {
                    final int number = i;
                    Task<Void> task = Task.call(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            Thread.sleep((long) (Math.random() * 1000));
                            if (number == 10) {
                                throw error;
                            }
                            return null;
                        }
                    });
                    tasks.add(task);
                }
                return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
                    @Override
                    public Void then(Task<Void> task) {
                        Assert.assertTrue(task.isCompleted());
                        Assert.assertTrue(task.isFaulted());
                        Assert.assertFalse(task.isCancelled());

                        Assert.assertFalse(task.getError() instanceof AggregateException);
                        Assert.assertEquals(error, task.getError());

                        for (Task<Void> t : tasks) {
                            Assert.assertTrue(t.isCompleted());
                        }
                        return null;
                    }
                });
            }
        });
    }

    public void testWhenAllTwoErrors() {
        final Exception error = new RuntimeException("This task failed.");

        runTaskTest(new Callable<Task<?>>() {
            @Override
            public Task<?> call() throws Exception {
                final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
                for (int i = 0; i < 20; i++) {
                    final int number = i;
                    Task<Void> task = Task.call(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            Thread.sleep((long) (Math.random() * 1000));
                            if (number == 10 || number == 11) {
                                throw error;
                            }
                            return null;
                        }
                    });
                    tasks.add(task);
                }
                return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
                    @Override
                    public Void then(Task<Void> task) {
                        Assert.assertTrue(task.isCompleted());
                        Assert.assertTrue(task.isFaulted());
                        Assert.assertFalse(task.isCancelled());

                        Assert.assertTrue(task.getError() instanceof AggregateException);
                        Assert.assertEquals(2, ((AggregateException) task.getError()).getErrors().size());
                        Assert.assertEquals(error, ((AggregateException) task.getError()).getErrors().get(0));
                        Assert.assertEquals(error, ((AggregateException) task.getError()).getErrors().get(1));

                        for (Task<Void> t : tasks) {
                            Assert.assertTrue(t.isCompleted());
                        }
                        return null;
                    }
                });
            }
        });
    }

    public void testWhenAllCancel() {
        runTaskTest(new Callable<Task<?>>() {
            @Override
            public Task<?> call() throws Exception {
                final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
                for (int i = 0; i < 20; i++) {
                    final Task<Void>.TaskCompletionSource tcs = Task.create();

                    final int number = i;
                    Task.call(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            Thread.sleep((long) (Math.random() * 1000));
                            if (number == 10) {
                                tcs.setCancelled();
                            } else {
                                tcs.setResult(null);
                            }
                            return null;
                        }
                    });

                    tasks.add(tcs.getTask());
                }
                return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
                    @Override
                    public Void then(Task<Void> task) {
                        Assert.assertTrue(task.isCompleted());
                        Assert.assertFalse(task.isFaulted());
                        Assert.assertTrue(task.isCancelled());

                        for (Task<Void> t : tasks) {
                            Assert.assertTrue(t.isCompleted());
                        }
                        return null;
                    }
                });
            }
        });
    }

    public void testAsyncChaining() {
        runTaskTest(new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                final ArrayList<Integer> sequence = new ArrayList<Integer>();
                Task<Void> result = Task.forResult(null);
                for (int i = 0; i < 20; i++) {
                    final int taskNumber = i;
                    result = result.continueWithTask(new Continuation<Void, Task<Void>>() {
                        public Task<Void> then(Task<Void> task) {
                            return Task.call(new Callable<Void>() {
                                public Void call() throws Exception {
                                    sequence.add(taskNumber);
                                    return null;
                                }
                            });
                        }
                    });
                }
                result = result.continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Assert.assertEquals(20, sequence.size());
                        for (int i = 0; i < 20; i++) {
                            Assert.assertEquals(i, sequence.get(i).intValue());
                        }
                        return null;
                    }
                });
                return result;
            }
        });
    }

    public void testOnSuccess() {
        Continuation<Integer, Integer> continuation = new Continuation<Integer, Integer>() {
            public Integer then(Task<Integer> task) {
                return task.getResult().intValue() + 1;
            }
        };
        Task<Integer> complete = Task.forResult(5).onSuccess(continuation);
        Task<Integer> error = Task.<Integer> forError(new IllegalStateException()).onSuccess(continuation);
        Task<Integer> cancelled = Task.<Integer> cancelled().onSuccess(continuation);

        Assert.assertTrue(complete.isCompleted());
        Assert.assertEquals(6, complete.getResult().intValue());
        Assert.assertFalse(complete.isFaulted());
        Assert.assertFalse(complete.isCancelled());

        Assert.assertTrue(error.isCompleted());
        Assert.assertTrue(error.getError() instanceof RuntimeException);
        Assert.assertTrue(error.isFaulted());
        Assert.assertFalse(error.isCancelled());

        Assert.assertTrue(cancelled.isCompleted());
        Assert.assertFalse(cancelled.isFaulted());
        Assert.assertTrue(cancelled.isCancelled());
    }

    public void testOnSuccessTask() {
        Continuation<Integer, Task<Integer>> continuation = new Continuation<Integer, Task<Integer>>() {
            public Task<Integer> then(Task<Integer> task) {
                return Task.forResult(task.getResult().intValue() + 1);
            }
        };
        Task<Integer> complete = Task.forResult(5).onSuccessTask(continuation);
        Task<Integer> error = Task.<Integer> forError(new IllegalStateException()).onSuccessTask(continuation);
        Task<Integer> cancelled = Task.<Integer> cancelled().onSuccessTask(continuation);

        Assert.assertTrue(complete.isCompleted());
        Assert.assertEquals(6, complete.getResult().intValue());
        Assert.assertFalse(complete.isFaulted());
        Assert.assertFalse(complete.isCancelled());

        Assert.assertTrue(error.isCompleted());
        Assert.assertTrue(error.getError() instanceof RuntimeException);
        Assert.assertTrue(error.isFaulted());
        Assert.assertFalse(error.isCancelled());

        Assert.assertTrue(cancelled.isCompleted());
        Assert.assertFalse(cancelled.isFaulted());
        Assert.assertTrue(cancelled.isCancelled());
    }

    public void testContinueWhile() {
        final AtomicInteger count = new AtomicInteger(0);
        runTaskTest(new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return Task.forResult(null).continueWhile(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        return count.get() < 10;
                    }
                }, new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        count.incrementAndGet();
                        return null;
                    }
                }).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) throws Exception {
                        Assert.assertEquals(10, count.get());
                        return null;
                    }
                });
            }
        });
    }

    public void testContinueWhileAsync() {
        final AtomicInteger count = new AtomicInteger(0);
        runTaskTest(new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return Task.forResult(null).continueWhile(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        return count.get() < 10;
                    }
                }, new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        count.incrementAndGet();
                        return null;
                    }
                });
            }
        });
    }
}
