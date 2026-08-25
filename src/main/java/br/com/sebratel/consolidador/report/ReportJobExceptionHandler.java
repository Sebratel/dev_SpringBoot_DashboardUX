package br.com.sebratel.consolidador.report;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReportJobExceptionHandler {

    @ExceptionHandler(ReportJobNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ReportJobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(ReportJobNotReadyException.class)
    public ResponseEntity<ApiError> handleNotReady(ReportJobNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(ReportFileNotAvailableException.class)
    public ResponseEntity<ApiError> handleReportFileNotAvailable(ReportFileNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage()));
    }

    public record ApiError(String message) {
    }
}
