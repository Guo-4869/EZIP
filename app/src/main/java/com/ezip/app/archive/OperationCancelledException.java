package com.ezip.app.archive;

import java.io.IOException;

/** Thrown when the user cancels a running archive operation. */
public class OperationCancelledException extends IOException {

    public OperationCancelledException() {
        super("cancelled by user");
    }
}
