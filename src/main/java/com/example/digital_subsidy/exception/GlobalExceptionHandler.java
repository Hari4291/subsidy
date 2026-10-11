package com.example.digital_subsidy.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        logger.error("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorTitle", "404 - Resource Not Found");
        return "error/error";
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicateResource(DuplicateResourceException ex, Model model) {
        logger.error("Duplicate resource error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorTitle", "Registration Error");
        return "error/error";
    }

    @ExceptionHandler(WorkflowException.class)
    public String handleWorkflowError(WorkflowException ex, Model model) {
        logger.error("Workflow processing error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorTitle", "Workflow Exception");
        return "error/error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        logger.warn("Validation error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorTitle", "Validation Error");
        return "error/error";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex, Model model) {
        logger.warn("Illegal state error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorTitle", "Action Not Allowed");
        return "error/error";
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public String handleMissingOrInvalidParams(Exception ex, Model model) {
        logger.warn("Invalid request parameters: {}", ex.getMessage());
        model.addAttribute("errorMessage", "Invalid request parameters: " + ex.getMessage());
        model.addAttribute("errorTitle", "Invalid Request Parameters");
        return "error/error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, Model model) {
        logger.warn("Max upload size exceeded: {}", ex.getMessage());
        model.addAttribute("errorMessage", "Uploaded file size exceeds the maximum limit of 5 MB.");
        model.addAttribute("errorTitle", "File Size Limit Exceeded");
        return "error/error";
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public void handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) throws org.springframework.security.access.AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("Unhandled exception occurred", ex);
        String msg = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "An unexpected system error occurred. Please try again later or contact portal support.";
        model.addAttribute("errorMessage", msg);
        model.addAttribute("errorTitle", "System Error");
        return "error/error";
    }
}
