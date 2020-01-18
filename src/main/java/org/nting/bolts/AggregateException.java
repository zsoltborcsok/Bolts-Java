/*
 * Copyright (c) 2014, Facebook, Inc. All rights reserved. This source code is licensed under the BSD-style license
 * found in the LICENSE file in the root directory of this source tree. An additional grant of patent rights can be
 * found in the PATENTS file in the same directory.
 */
package org.nting.bolts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates Exceptions that may be thrown in the process of a task's execution.
 */
public class AggregateException extends Exception {
    private static final long serialVersionUID = 1L;

    public AggregateException(List<Exception> errors) {
        super("There were multiple errors.");

        for (Exception error : errors) {
            addSuppressed(error);
        }
    }

    /**
     * Returns the list of errors that this exception encapsulates.
     */
    public List<Throwable> getErrors() {
        ArrayList<Throwable> exceptions = new ArrayList<Throwable>();
        Collections.addAll(exceptions, getSuppressed());
        return exceptions;
    }
}
